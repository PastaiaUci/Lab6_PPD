package org.example.client;

import org.example.model.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientProxy {
    private final String host;
    private final int port;
    private final AtomicInteger clientCount;
    private final String name;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private Socket connection;

    private BlockingQueue<Response> qresponses;
    private AtomicBoolean finished = new AtomicBoolean();
    private ExecutorService reader = Executors.newSingleThreadExecutor();

    public ClientProxy(String host, int port, AtomicInteger clientCount, String name) {
        this.host = host;
        this.port = port;
        this.clientCount = clientCount;
        this.name = name;
        //responses=new ArrayList<Response>();
        qresponses = new LinkedBlockingQueue<Response>();
        initializeConnection();
    }

    private void initializeConnection() {
        try {
            connection = new Socket(host, port);
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush();
            input = new ObjectInputStream(connection.getInputStream());
            startReader();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isFinished() {
        return finished.get();
    }

    public ProgramResponse sendProgramRequest(ProgramRequest request) {
        sendRequest(request);
        return (ProgramResponse) readResponse();
    }

    public OkResponse sendPaymentRequest() {
        sendRequest(new PayRequest());
        return (OkResponse) readResponse();
    }

    public OkResponse sendCancelRequest() {
        sendRequest(new CancellationRequest());
        return (OkResponse) readResponse();
    }

    private void sendRequest(Request request) {
        if (finished.get()) {
            return;
        }
        try {
            output.writeObject(request);
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Response readResponse() {
/*
        System.out.println("Reading resposne...");
*/
        Response response = null;
        if (finished.get()) {
            return null;
        }
        try {
            response = qresponses.take();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return response;
    }

    private void startReader() {
        reader.execute(new ReaderThread());
    }

    private void closeConnection() {
        finished.set(true);
        try {
            input.close();
            output.close();
            connection.close();
            reader.shutdownNow();
            int currentClient = clientCount.decrementAndGet();
            if (currentClient == 0) {
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ReaderThread implements Runnable {
        @Override
        public void run() {
            while (!finished.get()) {
                try {
                    Object response = input.readObject();
//                    System.out.println("response received " + response);
                    if (response instanceof ServerStopNotification) {
                        System.out.println("Got stop notification in client " + name);
                        closeConnection();
                        return;
                    } else {
                        try {
                            qresponses.put((Response) response);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Reading error " + e);
                }
            }
        }
    }
}
