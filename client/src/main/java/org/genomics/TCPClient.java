package org.genomics;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;

public class TCPClient {
    private String serverAddress;
    private int serverPort;

    public TCPClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public void sendMessage(String message) {
        try {
            // Cargar el truststore
            String truststorePath = "client/truststore.p12";
            String truststorePassword = "andres54";

            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream trustStream = new FileInputStream(truststorePath)) {
                trustStore.load(trustStream, truststorePassword.toCharArray());
            }

            // Inicializar TrustManager con el truststore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Crear contexto SSL
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, tmf.getTrustManagers(), null);

            // Crear socket seguro
            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(serverAddress, serverPort)) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println(message);
                System.out.println("Message sent to server.");

                String response;
                while ((response = in.readLine()) != null) {
                    System.out.println("Server response: " + response);
                    if (response.equals("SUCCESS")) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        TCPClient client = new TCPClient("localhost", 8080);
        client.sendMessage("Hello from client with truststore!");
    }
}
