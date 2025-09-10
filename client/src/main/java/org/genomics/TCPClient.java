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

                // Enviar mensaje al servidor
                out.println(message);
                System.out.println("Message sent to server.");


                String response;
                while ((response = in.readLine()) != null) {
                    System.out.println("Server response: " + response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        TCPClient client = new TCPClient("localhost", 8080);
        client.sendMessage(
                "CREATE_PATIENT\n" +
                        "full_name:Ana maría\n" +
                        "document_id:104801590\n" +
                        "age:22\n" +
                        "sex:M\n" +
                        "contact_email:María@example.com\n" +
                        "registration_date:2025-09-08\n" +
                        "clinical_notes:Test\n" +
                        "checksum_fasta:abcdds1234\n" +
                        "file_size_bytes:24\n" +
                        "FASTA_FILE\n" +
                        ">patient002\n" +
                        "ACGTACGTACGTACGTGCGT\n" +
                        "END_FASTA"
                    );
        client.sendMessage(
                "CREATE_PATIENT\n" + //Creamos un paciente nuevo con covid19 para probar
                        "full_name:Sebas Salazar\n" +
                        "document_id:200345678\n" +
                        "age:19\n" +
                        "sex:M\n" +
                        "contact_email:sebas@example.com\n" +
                        "registration_date:2025-09-09\n" +
                        "clinical_notes:Paciente con sintomas de covid\n" +
                        "checksum_fasta:xyz123covid\n" +
                        "file_size_bytes:12\n" +
                        "FASTA_FILE\n" +
                        ">patient003\n" +
                        "ACGTACGTACGTACGTTTGACCGTAGGACTGA\n" +
                        "END_FASTA"
        );
    }
}
