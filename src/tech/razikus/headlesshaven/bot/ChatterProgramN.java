package tech.razikus.headlesshaven.bot;

import tech.razikus.headlesshaven.*;
import tech.razikus.headlesshaven.bot.automation.AutoLoginCharCallback;
import tech.razikus.headlesshaven.bot.automation.BrodcastingChatCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatterProgramN extends AbstractProgram{
    private String progname;
    private WebHavenSessionManager manager;
    private Credential credential;
    private HashMap<String, String> runningArgs;

    public ChatterProgramN(String progname, WebHavenSessionManager manager, Credential credential, HashMap<String, String> runningArgs) {
        super(progname, manager, credential, runningArgs);
        this.progname = progname;
        this.manager = manager;
        this. credential = credential;
        this.runningArgs = runningArgs;
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
            session.addWidgetCallback(new AutoLoginCharCallback(altname, session));

            Thread sessionThread = new Thread(session);
            sessionThread.start();

            this.session = session;
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
                    if(message.equals("aa")){
                        isBanned.set(true);
                    }
                }
            });

            while(!isBanned.get()){
                ChatPseudoWidget c = session.getWidgetManager().getChatChannelByName("Infected");
                c.addErrorCallback( new ChatErrorCallback() {

                    @Override
                    public void onError(String message) {
                        System.out.println("CHAT ON ERROR");
                        isBanned.set(true);
                    }
                });
                c.sendMessage("ВАМ БАН");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            this.sessName = null;
            this.getManager().getSessions().remove(sessName);

            //создание нового персонажа на этом аккаунте
            try {
                Credential newCredentials = new Credential(credential.getUsername(), credential.getPassword(), "15");
                runningArgs.put("BEACON_PASS", "67845622");
                AbstractProgram pr = ProgramRegistry.instantiate("tech.razikus.headlesshaven.bot.CreateAltProgram", progname, manager, newCredentials, runningArgs);
                manager.startProgram(pr);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            setShouldClose(true);

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
                while(session.isAlive() && session.getLastState() != null && !this.isShouldClose()) {
                    Thread.sleep(300);
                    System.out.println("WAITING FOR FIRST STATE... ");
                    ArrayList<PseudoWidget> locwnd = session.getWidgetManager().getWidgetsByType("lbl");
                    if(!locwnd.isEmpty()){
                        Object text = "The position where you last logged out is not available, because that location is claimed. Would you like to restart at your hearth fire instead?";
                        for(PseudoWidget w : locwnd){
                            if(w.getCargs()[0].equals(text)){
                                PseudoWidget yesBtn = session.getWidgetManager().getWidgetButtonByLbl("Yes");
                                if(yesBtn != null){
                                    yesBtn.WidgetMsg("activate");
                                    System.out.println("Clicked YES in CHANGE POSITION window");
                                }
                            }
                        }
                    }
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
