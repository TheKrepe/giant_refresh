package dev.star1mp.giantrefresh.registry;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A lazily-created registry object. Each loader decides <i>when</i> it is registered
 * (Fabric: immediately, NeoForge: inside RegisterEvent); the value itself is created once.
 */
public final class RegEntry<T> implements Supplier<T> {
    private final ResourceKey<? extends Registry<? super T>> registry;
    private final Identifier id;
    private final Supplier<? extends T> factory;
    private T value;

    RegEntry(ResourceKey<? extends Registry<? super T>> registry, Identifier id, Supplier<? extends T> factory) {
        this.registry = registry;
        this.id = id;
        this.factory = factory;
    }

    @Override
    public synchronized T get() {
        if (value == null) {
            value = factory.get();
        }
        return value;
    }

    public ResourceKey<? extends Registry<? super T>> registry() {
        return registry;
    }

    public Identifier id() {
        return id;
    }
}
