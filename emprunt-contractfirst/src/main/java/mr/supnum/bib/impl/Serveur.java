package mr.supnum.bib.impl;

import jakarta.xml.ws.Endpoint;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Serveur {
    public static void main(String[] args) {
        String url = "http://localhost:8080/bib/EmpruntService";

        Endpoint endpoint = Endpoint.create(new EmpruntServiceImpl());

        // Sous « mvn exec:java », les threads HTTP héritent du classloader de Maven
        // et ne voient pas l'implémentation JAXB : les Faults échouent alors en 500
        // sans corps. On force donc le classloader de l'application sur ces threads.
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "bib-http");
            t.setContextClassLoader(Serveur.class.getClassLoader());
            return t;
        });
        endpoint.setExecutor(executor);

        endpoint.publish(url);
        System.out.println("WSDL : " + url + "?wsdl");
    }
}
