package tech.razikus.headlesshaven.bot;

import tech.razikus.headlesshaven.*;
import tech.razikus.headlesshaven.bot.automation.AutoLoginCharCallback;
import tech.razikus.headlesshaven.bot.automation.BrodcastingChatCallback;
import tech.razikus.headlesshaven.bot.automation.OnCharLoggedInWaiter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatterProgram extends AbstractProgram{


    public ChatterProgram(String progname, WebHavenSessionManager manager, Credential credential, HashMap<String, String> runningArgs) {
        super(progname, manager, credential, runningArgs);
    }


    private WebHavenSession session;
    private String sessName;

    @Override
    public void run() {
        while (!this.isShouldClose()) {
            WebHavenSessionManager manager = this.getManager();
            String username = this.getCredential().getUsername();
            String password = this.getCredential().getPassword();
            String altname = this.getCredential().getCharname();

            String sessName = username + "-" + altname;
            this.sessName = sessName;
            if(manager.getSessions().containsKey(sessName)) {
                return;
            }

            BrodcastingChatCallback chatStreamer = new BrodcastingChatCallback(this.getManager(), sessName) {
                @Override
                public void onChatMessage(ChatMessage message) {
                    super.onChatMessage(message);
                    System.out.println(message.toString());
                }
            };

            CopyOnWriteArrayList<ChatCallback> callbacks = new CopyOnWriteArrayList<>();
            callbacks.add(chatStreamer);
            WebHavenSession session = new WebHavenSession(username, password, callbacks);
            try {
                session.authenticate();
            } catch (InterruptedException e) {
                setShouldClose(true);
                return;
            }

            OnCharLoggedInWaiter waiter = new OnCharLoggedInWaiter();
            session.addWidgetCallback(new AutoLoginCharCallback(altname, session, waiter));

            Thread sessionThread = new Thread(session);
            sessionThread.start();


            WebHavenSession sessionWaited = waiter.waitForSession();
            if(sessionWaited.isSessionTeleported()) {
                sessionThread.interrupt();
                sessionThread = new Thread(sessionWaited);
                sessionThread.start();
            }

            this.session = sessionWaited;
            Thread programThread = new Thread(this::sessionHandler);
            programThread.start();

            this.getManager().getSessions().put(sessName, session);

            while (!session.connectionCreated()){
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            while(session.getWidgetManager().getChatChannelByName("Area Chat") == null){
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            AtomicBoolean isBanned = new AtomicBoolean(false);

            session.getWidgetManager().addErrorCallback(new PseudoWidgetErrorCallback() {
                @Override
                public void onError(String message) {
                    if(message.equals("")){
                        isBanned.set(true);
                    }
                }
            });

            while(!isBanned.get()){
                session.getWidgetManager().getChatChannelByName("Area Chat").sendMessage("ВАМ БАН");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            //создание нового персонажа на этом аккаунте

            try {
                programThread.join();
            } catch (InterruptedException e) {
                this.setShouldClose(true);
            }
            this.sessName = null;
            this.getManager().getSessions().remove(sessName);

            try {
                // WAIT 10 SECONDS BEFORE RECONNECT
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                setShouldClose(true);
            }

        }
    }

    @Override
    public void sessionHandler() {

        while (!session.connectionCreated() && !this.isShouldClose()) {
            try {

                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("WAITING FOR CONNECTION... ");
        }

        while (session.isAlive() && !this.isShouldClose()) {
            WebHavenState state = null; // Get state from queue
            try {
                while(session.isAlive() && session.getLastState() == null && !this.isShouldClose()) {
                    Thread.sleep(100);
                    System.out.println("WAITING FOR FIRST STATE... ");
                }
                state = session.getLastState();
            } catch (InterruptedException e) {
                this.setShouldClose(true);
            }
            if(state != null && !this.isShouldClose()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    this.setShouldClose(true);
                }

                this.getManager().broadcastState(sessName, getProgramInformation(), state);
            }
        }
    }

    @Override
    public HashSet<String> getRunningSessions() {
        HashSet<String> newSessions = new HashSet<>();
        if(this.sessName != null) {
            newSessions.add(sessName);
        }
        return newSessions;
    }

    @Override
    public void setShouldClose(boolean shouldClose) {
        if(this.session != null) {
            this.session.setShouldClose(true);
        }
        super.setShouldClose(shouldClose);
    }
}
