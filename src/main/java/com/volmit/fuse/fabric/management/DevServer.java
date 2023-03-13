package com.volmit.fuse.fabric.management;

import com.volmit.fuse.fabric.Fuse;
import com.volmit.fuse.fabric.management.data.Project;
import com.volmit.fuse.fabric.util.Looper;
import com.volmit.fuse.fabric.util.ProcessRelogger;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DevServer extends Thread {
    private final Runnable onStarted;
    private final Consumer<String> serverLog;
    @Getter
    private final List<String> logs;
    private final Map<String, String> debugKeys = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDebug = new ConcurrentHashMap<>();
    private PrintWriter outputWriter;
    private String player;
    private boolean closeClient = false;
    private Process process;

    public DevServer(Runnable onStarted) {
        logs = new ArrayList<>();
        this.onStarted = onStarted;
        this.serverLog = this::onServerLog;
        Looper looper = new Looper() {
            @Override
            protected long loop() {
                serverCommand("fuse keepalive");
                return 7000;
            }
        };
        looper.start();
    }

    public Map<String, Long> getDebugTimes() {
        return lastDebug;
    }

    public Map<String, String> getDebugKeys() {
        synchronized (debugKeys) {
            for (String i : new ArrayList<>(debugKeys.keySet())) {
                if (lastDebug.get(i) == null || System.currentTimeMillis() - lastDebug.get(i) > 10000) {
                    lastDebug.remove(i);
                    debugKeys.remove(i);
                }
            }
        }

        return debugKeys;
    }

    public void putDebugKey(String k, String v) {
        synchronized (debugKeys) {
            debugKeys.put(k, v);
            lastDebug.put(k, System.currentTimeMillis());
        }
    }

    public void installFuse(File file) throws IOException {
        FileUtils.copyFile(file, new File(Fuse.service.getServerFolder(), "plugins/Fuse.jar"));
        Fuse.log("Installed Fuse to Server");
    }

    public void installPlugin(Project project, File file, Project p) throws IOException {
        if(p.getOnUnload() != null) {
            for(String i : p.getOnUnload()) {
                serverCommand(i);
            }
        }

        new File(Fuse.service.getServerFolder(), "plugins/fuse").mkdirs();
        Fuse.log("Installing Plugin " + project.getName() + " to Server");
        Fuse.log("Copying " + file.getAbsolutePath() + " to " + new File(Fuse.service.getServerFolder(), "plugins/fuse/" + project.getName() + ".jar").getAbsolutePath());
        FileUtils.copyFile(file, new File(Fuse.service.getServerFolder(), "plugins/fuse/" + project.getName() + ".jar"));
        Fuse.log("Installed Plugin " + project.getName() + " to Server");
        serverCommand("fuse inject");
        if(p.getOnLoad() != null) {
            Fuse.service.getExecutor().queue(() -> {
                if(p.getOnLoad() != null) {
                    for(String i : p.getOnLoad()) {
                        serverCommand(i);
                    }
                }
            });
        }
    }

    private void onServerOnline() {
        Fuse.log("Server Online");
        serverCommand("say Fuse Management Online!");
        onStarted.run();
    }

    private void onServerLog(String line) {
        if (line.contains("@debug ")) {
            try {
                String[] s = line.split("\\Q@debug \\E");
                String[] k = s[1].split("\\Q \\E");
                putDebugKey(k[0], k[1]);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else {
            logs.add(line);
        }

        if (line.contains("Done") && line.contains("s)! For help, type \"help\"")) {
            onServerOnline();
        }

        if (line.contains(player + " issued server command: /")) {
            onPlayerCommand(line.split("\\Q" + player + " issued server command: /\\E")[1]);
        }

        if (line.endsWith("left the game")) {
            onPlayerLeft();
        }

        if (line.endsWith("joined the game")) {
            String player = "";
            for (String i : line.split("\\Q \\E")) {
                if (i.endsWith("joined")) {
                    serverCommand("op " + player);
                    this.player = player;
                    onPlayerJoined(player);
                    break;
                }

                player = i;
            }


        }
    }

    public void messagePlayer(String message) {
        serverCommand("tell " + player + " " + message);
    }

    private void onPlayerLeft() {

    }

    private void onPlayerJoined(String player) {
        messagePlayer("Fuse Management is Online! Happy Hacking!");
        messagePlayer("Configure your workspace with [SEMICOLON]");
        if(Fuse.service.getWorkspace().getOnJoin() != null) {
            Fuse.service.getExecutor().queue(() -> {
                if(Fuse.service.getWorkspace().getOnJoin() != null) {
                    for(String i : Fuse.service.getWorkspace().getOnJoin()) {
                        serverCommand(i.replaceAll("\\Q{player}\\E", player));
                    }
                }
            });
        }
    }

    public void onPlayerCommand(String command) {
        Fuse.log("Player Command: " + command);

        if (command.equalsIgnoreCase("crash")) {
            stopServer();
            closeClient = true;
        }
    }

    public void serverCommand(String command) {
        if (outputWriter != null) {
            outputWriter.println(command);
            outputWriter.flush();
        }
    }

    public void reloadServer() {
        serverCommand("reload");
    }

    public void stopServer() {
        serverCommand("stop");
        if (process != null) {
            try {
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                process.destroy();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void run() {
        String javaRoot = new File(Fuse.service.getJdkLocation(), "bin/java").getAbsolutePath();
        List<String> args = new ArrayList<>();
        args.add(javaRoot);
        //-Xmx8g -Xms8g -XX:+UnlockExperimentalVMOptions -XX:+UseZGC
        args.add("-Xmx8g");
        args.add("-Xms1m");
        args.add("-XX:+UnlockExperimentalVMOptions");
        args.add("-XX:+UseZGC");
        args.add("-XX:SoftMaxHeapSize=2G");
        args.add("-XX:ZUncommitDelay=30");
        args.add("-DIReallyKnowWhatIAmDoingISwear");
        args.add("-jar");
        args.add(Fuse.service.getServerExecutable().getAbsolutePath());
        args.add("nogui");

        Fuse.log("Executing: " + String.join(" ", args));
        Fuse.log(Fuse.service.getFuseDataFolder().getAbsolutePath());
        try {
            new File(Fuse.service.getServerFolder(), "STOPPER").mkdirs();
            Process p = new ProcessBuilder()
                    .command(args.toArray(new String[0]))
                    .directory(Fuse.service.getServerFolder())
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));
            new ProcessRelogger(p.getInputStream(), false, serverLog).start();
            new ProcessRelogger(p.getErrorStream(), false, serverLog).start();
            OutputStream output = p.getOutputStream();
            outputWriter = new PrintWriter(output);
            process = p;
            int code = p.waitFor();
            Fuse.log("Process Exited With Code " + code);

            if (closeClient) {
                Fuse.service.close();
                MinecraftClient.getInstance().stop();
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
