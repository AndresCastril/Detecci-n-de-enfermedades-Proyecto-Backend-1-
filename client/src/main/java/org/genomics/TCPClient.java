package org.genomics;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    // Calcular checksum automáticamente con SHA-256
    private static String calculateChecksum(String fastaSequence) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(fastaSequence.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            e.printStackTrace();
            return "invalid_checksum";
        }
    }

    public static void main(String[] args) {
        TCPClient client = new TCPClient("localhost", 8080);
        java.util.Scanner scanner = new java.util.Scanner(System.in);

        while (true) {
            System.out.println("\n=== MENU ===");
            System.out.println("1. Create patient");
            System.out.println("2. Get patient by ID");
            System.out.println("3. Delete patient by ID");
            System.out.println("4. Update patient by ID");
            System.out.println("5. Exit");

            String opcion = scanner.nextLine();

            if (opcion.equals("1")) {
                System.out.print("Full name: ");
                String fullName = scanner.nextLine();
                System.out.print("Personal ID: ");
                String doc = scanner.nextLine();
                System.out.print("Age: ");
                String age = scanner.nextLine();
                System.out.print("Sex (M/F): ");
                String sex = scanner.nextLine();
                System.out.print("Email: ");
                String email = scanner.nextLine();
                System.out.print("Date of resgister (YYYY-MM-DD): ");
                String date = scanner.nextLine();
                System.out.print("clinical notes: ");
                String notes = scanner.nextLine();
                System.out.print("FASTA sequence (without '>'): ");
                String fastaSeq = scanner.nextLine();

                //Generar checksum automáticamente
                String checksum = calculateChecksum(fastaSeq);
                String fileSize = String.valueOf(fastaSeq.length());

                String message =
                        "CREATE_PATIENT\n" +
                                "full_name:" + fullName + "\n" +
                                "document_id:" + doc + "\n" +
                                "age:" + age + "\n" +
                                "sex:" + sex + "\n" +
                                "contact_email:" + email + "\n" +
                                "registration_date:" + date + "\n" +
                                "clinical_notes:" + notes + "\n" +
                                "checksum_fasta:" + checksum + "\n" +
                                "file_size_bytes:" + fileSize + "\n" +
                                "FASTA_FILE\n" +
                                ">" + fullName.replace(" ", "_") + "\n" +
                                fastaSeq + "\n" +
                                "END_FASTA";

                client.sendMessage(message);

            } else if (opcion.equals("2")) {
                System.out.print("Patient ID: ");
                String id = scanner.nextLine();
                String message = "GET_PATIENT " + id + "\nEND_FASTA";
                client.sendMessage(message);

            } else if (opcion.equals("3")) {
                System.out.print("Patient ID: ");
                String id = scanner.nextLine();
                String message = "DELETE_PATIENT " + id + "\nEND_FASTA";
                client.sendMessage(message);

            } else if (opcion.equals("4")) {
                System.out.print("Patient ID for updating: ");
                String id = scanner.nextLine();

                System.out.print("Full name: ");
                String fullName = scanner.nextLine();
                System.out.print("Personal ID: ");
                String doc = scanner.nextLine();
                System.out.print("Age: ");
                String age = scanner.nextLine();
                System.out.print("Sex (M/F): ");
                String sex = scanner.nextLine();
                System.out.print("Email: ");
                String email = scanner.nextLine();
                System.out.print("Date of resgister (YYYY-MM-DD): ");
                String date = scanner.nextLine();
                System.out.print("clinical notes: ");
                String notes = scanner.nextLine();
                System.out.print("FASTA sequence (without '>'): ");
                String fastaSeq = scanner.nextLine();

                // checksum automático también en UPDATE
                String checksum = calculateChecksum(fastaSeq);
                String fileSize = String.valueOf(fastaSeq.length());

                String message =
                        "UPDATE_PATIENT " + id + "\n" +
                                "full_name:" + fullName + "\n" +
                                "document_id:" + doc + "\n" +
                                "age:" + age + "\n" +
                                "sex:" + sex + "\n" +
                                "contact_email:" + email + "\n" +
                                "registration_date:" + date + "\n" +
                                "clinical_notes:" + notes + "\n" +
                                "checksum_fasta:" + checksum + "\n" +
                                "file_size_bytes:" + fileSize + "\n" +
                                "FASTA_FILE\n" +
                                ">" + fullName.replace(" ", "_") + "\n" +
                                fastaSeq + "\n" +
                                "END_FASTA";

                client.sendMessage(message);

            } else if (opcion.equals("5")) {
                System.out.println("Exiting...");
                break;
            } else {
                System.out.println("Invalid input.");
            }
        }


        scanner.close();
    }
}
