import java.io.*;
import java.util.*;
import java.nio.file.*;

public class Busqueda {
    //crea la estructura que contendrá la información del docmento
    private static class ResultadoDocumento {
        String nombreDocumento;
        double puntuacion;

        ResultadoDocumento(String nombreDocumento, double puntuacion) {
            this.nombreDocumento = nombreDocumento;
            this.puntuacion = puntuacion;
        }
    }
    //método para convertir txt a json


    //comienza el motor de búsqueda interactivo
    public static void start(String[] args) throws IOException{
        System.out.println("\nMotor de búsqueda:");

        while(true){
            System.out.println("\nSelecciona el tipo de búsqueda:");
            System.out.println("1. Búsqueda de un término");
            System.out.println("2. Búsqueda con operadores (AND/OR)");
            System.out.println("3. Búsqueda de frase");
            System.out.println("4. Salir");
            System.out.print("Opción: ");

            int opcion;
            try {
                opcion = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Opción no válida. Introduce un número del 1 al 4.");
                continue;
            }

            if (opcion == 4) break;

            switch (opcion) {
                case 1:
                    busquedaTermino();
                    break;
                case 2:
                    busquedaOperadores();
                    break;
                case 3:
                    busquedaFrase();
                    break;
                default:
                    System.out.println("Opción no válida. Introduce un número del 1 al 4.");
                    continue;
            }

        }
    }
    //método para la búsqueda de un término
    private void busquedaTermino() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Introduce el término a buscar: ");
        String consulta = scanner.nextLine().trim();
        String terminoP = procesarTermino(consulta);
    }

    //método para la búsqueda con operadores
    private void busquedaOperadores() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Introduce la consulta con operadores (and/or): ");
        String consulta = scanner.nextLine().trim();
    }

    //método para la búsqueda de una frase
    private void busquedaFrase() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Introduce la frase a buscar (ej: \"índice invertido\"): ");
        String consulta = scanner.nextLine().trim();
        List<String> terminosP = procesarConsulta(consulta);
    }

    //método para procesar un sólo término
    private String procesarTermino(String consulta){
        System.out.print("Consulta original ");
        String termino = consulta;
        termino = termino.toLowerCase();
        termino = termino.replaceAll("[^a-z0-9\\s-]", " ");
        termino = termino.replaceAll("\\b[0-9]+\\b", " ");
        termino = termino.replaceAll("\\s+", " ");
        termino = termino.replaceAll("-+ | -+", " ");
        termino = termino.trim();

        if (termino.length() < 3) return "";

        try {
            Set<String> stopwords = cargarStopwords("stopwords.txt");
            termino = aplicarStemming(termino);
            if (stopwords.contains(termino)) {
                return "";
            }
        } catch (IOException e) {
            System.err.println("Error al cargar stopwords: " + e.getMessage());
        }


        System.out.print("Consulta procesada: " "+ termino");
        return termino;
    }

    //método para procesar una consulta de varios términos
    private List<String> procesarConsulta(String consulta){
        System.out.print("Consulta original ");
        consulta = consulta.toLowerCase();
        consulta = consulta.replaceAll("[^a-z0-9\\s-]", " ");
        consulta = consulta.replaceAll("\\b[0-9]+\\b", " ");
        consulta = consulta.replaceAll("\\s+", " ");
        consulta = consulta.replaceAll("-+ | -+", " ");
        consulta = consulta.trim();

        List<String> terminos = Arrays.asList(consulta.split(" "));
        List<String> terminosProcesados = new ArrayList<>();

        try {
            Set<String> stopwords = cargarStopwords("stopwords.txt");
            for (String termino : terminos) {
                if (termino.length() < 3) continue;
                String raiz = aplicarStemming(termino);
                if (!stopwords.contains(raiz)) {
                    terminosProcesados.add(raiz);
                }
            }
        } catch (IOException e) {
            System.err.println("Error al cargar stopwords: " + e.getMessage());
        }

        System.out.print("Consulta procesada: " );
        for(String termino :terminosProcesados) {
            System.out.print(termino);
        }
        return terminosProcesados;
    }
    //método para aplicar el stemming
    private String aplicarStemming(String termino) {
        if (termino.endsWith("s")) termino = termino.substring(0, termino.length() - 1);
        if (termino.endsWith("ed")) termino = termino.substring(0, termino.length() - 2);
        if (termino.endsWith("es")) termino = termino.substring(0, termino.length() - 2);
        if (termino.endsWith("ing")) termino = termino.substring(0, termino.length() - 3);
        if (termino.endsWith("ies")) termino = termino.substring(0, termino.length() - 3) + "y";
        return termino;
    }
    // método para cargar stopwords
    private static Set<String> cargarStopwords(String ruta) throws IOException{
        Set<String> stopwords = new HashSet<>();
        Files.lines(Paths.get(ruta))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(stopwords::add);
        return stopwords;
    }

}