package dev.koifih.client.module.impl.render;

import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.blocks.BlockIndex;
import dev.koifih.client.render.blocks.BlockRenderer;
import dev.koifih.client.setting.BlockSetting;
import dev.koifih.client.setting.ColorSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.ui.Theme;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import java.util.HashSet;
import java.util.Set;

public final class BlockEsp extends Module {
    private final BlockSetting blocks = add(new BlockSetting("blocks"));
    private final ColorSetting color = add(new ColorSetting("color", Theme.DEFAULT_ACCENT));
    private final SliderSetting fillOpacity = add(new SliderSetting("fillOpacity", 25, 0, 100, Measure.PERCENT));
    private final SliderSetting lineWidth = add(new SliderSetting("lineWidth", 2, 1, 6));
    private Set<String> tracked = Set.of();

    public BlockEsp() {
        super("blockEsp");
    }

    @Override
    public String info() {
        return Integer.toString(BlockRenderer.visibleBlocks());
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, this::onTick);
        apply();
    }

    @Override
    protected void onDisable() {
        BlockIndex.configure(Set.of());
        BlockRenderer.configure(null);
    }

    private void onTick(PreTickEvent event) {
        if (mc.player == null) return;
        if (!blocks.get().equals(tracked)) apply();
        else BlockRenderer.configure(spec());
    }

    private void apply() {
        tracked = Set.copyOf(blocks.get());
        Set<Block> selected = new HashSet<>();
        for (String id : tracked) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null) BuiltInRegistries.BLOCK.getOptional(identifier).ifPresent(selected::add);
        }
        BlockRenderer.configure(spec());
        BlockIndex.configure(selected);
    }

    private BlockRenderer.Spec spec() {
        return new BlockRenderer.Spec(color.get(), fillOpacity.get() / 100f, lineWidth.get());
    }
}
