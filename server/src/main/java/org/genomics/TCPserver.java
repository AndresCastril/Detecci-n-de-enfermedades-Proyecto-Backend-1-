package org.genomics;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;

public class TCPServer {
    private int serverPort;
    private static AtomicInteger patientCounter = new AtomicInteger(1); // genera patient_id

    public TCPServer(int serverPort) {
        this.serverPort = serverPort;
    }

    public void start() {
        try {
            // Ruta al keystore
            String keystorePath = "server/certificate/keystore.p12";
            String keystorePassword = "andres54";

            // Cargar keystore
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream keyStoreStream = new FileInputStream(keystorePath)) {
                keyStore.load(keyStoreStream, keystorePassword.toCharArray());
            }


            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());

            // Crear contexto SSL
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Crear socket seguro
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(serverPort);

            System.out.println("Server started on port: " + serverPort);

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                handleClient(clientSocket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(SSLSocket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            StringBuilder request = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                request.append(line).append("\n");
                if (line.equals("END_FASTA")) {
                    break;
                }
            }

            System.out.println("Received message:\n" + request);

            if (request.toString().startsWith("CREATE_PATIENT")) {
                int newId = patientCounter.getAndIncrement();
                out.println("SUCCESS");
                out.println("patient_id:" + newId);
            } else {
                out.println("ERROR");
                out.println("message:Unknown command");
            }

            System.out.println("Response sent, closing connection.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) {
        TCPServer server = new TCPServer(8080);
        server.start();
    }
}
