package server;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.ServerRuntimeException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;




@RequiredArgsConstructor
public class Server {

    private final int port;
    private final int numberOfThreads;
    private ExecutorService executor;

    private ServerSocket server=null;



    public  void start() {
         executor =  Executors.newFixedThreadPool(numberOfThreads);

        try{
            server=new ServerSocket(port);
            while(true){
                System.out.println("Waiting for clients ...");
                Socket client=server.accept();
                System.out.println("Client connected ...");
                processRequest(client);
            }
        } catch (IOException e) {
            throw new RuntimeException("Starting server errror ",e);
        }finally {
            stop();
        }


    }

    private void processRequest(Socket client) {


    }

    private void stop() {
    }


}
