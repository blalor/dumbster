/*
 * Dumbster - a dummy SMTP server
 * Copyright 2004 Jason Paul Kitchen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dumbster.smtp;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

/**
 * Dummy SMTP server for testing purposes.
 */
public class SimpleSmtpServer implements Runnable {
    private volatile MailStore mailStore = new RollingMailStore();
    public static final int DEFAULT_SMTP_PORT = 25;
    private volatile boolean stopped = true;
    private ServerSocket serverSocket;
    private int port = DEFAULT_SMTP_PORT;
    private static final int SERVER_SOCKET_TIMEOUT = 500;
    private volatile boolean threaded = false;

    public SimpleSmtpServer() {
    }

    public SimpleSmtpServer(int port) {
        this.port = port;
    }

    public void run() {
        stopped = false;
        try {
            initializeServerSocket();
            serverLoop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initializeServerSocket() throws Exception {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(SERVER_SOCKET_TIMEOUT);
        Thread.sleep(100);
        synchronized (this) {
            notifyAll();
        }
    }

    private void serverLoop() throws IOException {
        while (!isStopped()) {
            Socket socket = clientSocket();
            if (socket == null)
                continue;
            synchronized (this) {
                SocketWrapper source = new SocketWrapper();
                source.setSocket(socket);
                ClientSession transaction = new ClientSession(source, mailStore);
                if (threaded) {
                    Thread t = new Thread(transaction);
                    try {
                        t.join();
                        t.start();
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    transaction.run();
                }

            }
        }
    }

    private Socket clientSocket() {
        Socket socket = null;
        try {
            socket = serverSocket.accept();
        } catch (IOException ignored) {
        }
        return socket;
    }

    public synchronized boolean isStopped() {
        return stopped;
    }

    public synchronized void stop() {
        stopped = true;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    public MailMessage[] getMessages() {
        return mailStore.getMessages();
    }

    public MailMessage getMessage(int i) {
        return mailStore.getMessage(i);
    }

    public int getEmailCount() {
        return mailStore.getEmailCount();
    }

    public static SimpleSmtpServer start() {
        return start(DEFAULT_SMTP_PORT);
    }

    public void anticipateMessageCountFor(int messageCount, int ticks) {
        int tickdown = ticks;
        while (mailStore.getEmailCount() < messageCount && tickdown > 0) {
            tickdown--;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public static SimpleSmtpServer start(int port) {
        SimpleSmtpServer server = new SimpleSmtpServer(port);
        Thread t = new Thread(server);
        t.start();

        synchronized (server) {
            try {
                server.wait();
            } catch (InterruptedException ignored) {
            }
        }
        return server;
    }

    /**
     * Toggles if the SMTP server is single or multi-threaded for response to
     * SMTP sessions.
     *
     * @param threaded
     */
    public void setThreaded(boolean threaded) {
        this.threaded = threaded;
    }

    public void setMailStore(MailStore mailStore) {
        this.mailStore = mailStore;
    }

}
