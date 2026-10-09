package group.aelysium.rustyconnector.modules.postgres;


import group.aelysium.rustyconnector.common.haze.HazeProvider;
import group.aelysium.rustyconnector.common.modules.ExternalModuleBuilder;
import group.aelysium.rustyconnector.common.modules.Module;
import group.aelysium.rustyconnector.proxy.ProxyKernel;
import group.aelysium.rustyconnector.server.ServerKernel;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.function.Consumer;

public class Builder extends ExternalModuleBuilder<Module> {
    private static final Consumer<HazeProvider> register = haze -> {
        try {
            File directory = new File("rc-modules/rcm-postgres");
            if(!directory.exists()) directory.mkdirs();

            {
                File[] files = directory.listFiles();
                if (files == null || files.length == 0)
                    DatabaseConfig.New("default");
            }

            File[] files = directory.listFiles();
            if (files == null) return;
            if (files.length == 0) return;

            for (File file : files) {
                if (!(file.getName().endsWith(".yml") || file.getName().endsWith(".yaml"))) continue;
                int extensionIndex = file.getName().lastIndexOf(".");
                String name = file.getName().substring(0, extensionIndex);
                haze.registerDatabase(DatabaseConfig.New(name).builder());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    public void bind(@NotNull ProxyKernel kernel, @NotNull Module instance) {
        kernel.fetchModule("haze").onStart(h->{
            HazeProvider haze = (HazeProvider) h;
            register.accept(haze);
        });
    }

    public void bind(@NotNull ServerKernel kernel, @NotNull Module instance) {
        kernel.fetchModule("haze").onStart(h->{
            HazeProvider haze = (HazeProvider) h;
            register.accept(haze);
        });
    }

    @NotNull
    @Override
    public Module onStart(@NotNull Context context) throws Exception {
        return new Module() {
            @Override
            public @Nullable Component details() {
                return Component.text("The Postgres Haze Driver doesn't contain any native details for you. Check the Haze provider for haze details.");
            }

            @Override
            public void close() throws Exception {}
        };
    }
}