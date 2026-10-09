package group.aelysium.rustyconnector.modules.postgres;

import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.common.haze.HazeDatabase;
import group.aelysium.rustyconnector.common.modules.Module;
import group.aelysium.rustyconnector.shaded.group.aelysium.declarative_yaml.DeclarativeYAML;
import group.aelysium.rustyconnector.shaded.group.aelysium.declarative_yaml.annotations.*;
import group.aelysium.rustyconnector.shaded.group.aelysium.declarative_yaml.lib.Printer;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Map;

@Namespace("rustyconnector-modules")
@Config("/rcm-postgres/{name}.yml")
@Comment({
        "############################################################",
        "#||||||||||||||||||||||||||||||||||||||||||||||||||||||||||#",
        "#                    Postgres Haze Database                   #",
        "#                                                          #",
        "#               ---------------------------                #",
        "#                                                          #",
        "# | Setup your database! The target database name          #",
        "# | is the same as the name you give this file!            #",
        "#                                                          #",
        "# | To make a new database connection, just duplicate this #",
        "# | template, rename it, and configure it how you'd like!  #",
        "#                                                          #",
        "#               ---------------------------                #",
        "#                                                          #",
        "#||||||||||||||||||||||||||||||||||||||||||||||||||||||||||#",
        "############################################################",
})
public class DatabaseConfig {
    @PathParameter("name")
    private String name;

    @Node(0)
    private String database = "database";
    @Node(1)
    private String address = "127.0.0.1";
    @Node(2)
    private int port = 5432;
    @Node(3)
    private String username = "root";
    @Node(4)
    private String password = "admin";
    @Node(5)
    private int maxPoolSize = 100;
    
    public @NotNull Module.Builder<HazeDatabase> builder() {
        return new Module.Builder<>(name, "A Postgres connection that connects to the database " + name + ".") {
            @Override
            public PostgresDatabase get() {
                try {
                    return new PostgresDatabase(
                        database,
                        address,
                        port,
                        username,
                        password,
                        maxPoolSize
                    );
                } catch (SQLException e) {
                    RC.Error(Error.from(e).urgent(true).whileAttempting("To initialize the postgres connection."));
                }
                return null;
            }
        };
    }

    public static DatabaseConfig New(@NotNull String name) {
        return DeclarativeYAML.From(
                DatabaseConfig.class,
                new Printer().pathReplacements(Map.of(
                        "name", name
                ))
        );
    }
}