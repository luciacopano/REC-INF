import java.io.*;
import java.util.*;
import java.nio.file.*;

public class Main{
    // Indice invertido
    static class IndiceInvertido{
        double idf = 0.0;
        Map<String, Double> documentos = new HashMap<>(); // Mapa de documento a tf-idf
    }

    public static void main(String[] args) {
        System.out.println("Iniciando el procesamiento de documentos...");
        try {
            // Crawler
            ejecutarCrawler("corpus");
            // Indice invertido
            crearIndice("corpus", "output/index.txt", "output/lengths.txt");

            System.out.println("Indexacion completada");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();    // Imprime la traza del error
        }
    }

    //  no existe la carpeta corpus en local, el index.html ha sido actualizado desde la última vez que se visitó
    private static void ejecutarCrawler(String Corpus) throws IOException{
        Path corpusPath = Paths.get(Corpus);
        if(!Files.exists(corpusPath)){
            System.out.println("La carpeta corpus no existe → CREANDO...");
            descargarDocumentosWeb();
            return;
        }

        if(!Files.list(corpusPath).findAny().isPresent()){
            System.out.println("El corpus está vacío → DESCARGANDO...");
            descargarDocumentosWeb();
            return;
        }

        System.out.println("La carpeta corupus ya existe → SKIP");
    }

    // Descarga de documentos del index.html
    private static void descargarDocumentosWeb(){
        System.out.println("Descargando documentos web...");
        // Aquí iría la lógica para descargar los documentos web
        System.out.println("Descarga completada.");
    }

    // INDEXACION
    public static void crearIndice(String Corpus, String indicePath, String longitudesPath) throws IOException{
        
        // Stopwords
        System.out.println("\n CARGANDO STOPWORDS...");
        Set<String> stopwords = cargarStopwords("stopwords.txt");

        // Procesar documentos
        System.out.println("PROCESANDO DOCUMENTOS...");
        Map<String, InfoTermino> indiceInvertido = new HashMap<>();
        List<String> listaDocumentos = new ArrayList<>();

        // Recorrer el corpus
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(Corpus))){
            for(Path archivo : stream){
                if(!Files.isRegularFile(archivo)) continue;     // Saltar si no es un archivo regular

                String docId = archivo.getFileName().toString();
                listaDocumentos.add(docId);

                // Procesar el documento
                String texto = Files.readString(archivo);
                String textoLimpio = limpiarTexto(texto);   
                String[] terminos = dividirEnTerminos(textoLimpio);
                List<String> terminosFiltrados = filtrarTerminos(terminos, stopwords);  // Filtro términos + Stemming
                
                // Calcular TF
                Map<String, Integer> frecuencias = contarFrecuencias(terminosFiltrados);
                for(Map.Entry<String, Integer> entrada : frecuencias.entrySet()){
                    String termino = entrada getKey();
                    int frec = entrada.getValue();
                    double tf = calcularTF(frec);

                    // Añadir el indice invertido
                    // Si el término no existe, crear nueva entrada
                    InfoTermino info = indiceInvertido.computeIfAbsent(termino, k -> new InfoTermino());
                    info.documentos.put(docId, tf);
                }
                if(listaDocumentos.size() % 20 == 0){
                    System.out.println(" → " + listaDocumentos.size() + " documentos procesados...");
                }
            }
        }

        int totalDocs = listaDocumentos.size();
        System.out.println("Total: " + totalDocs + " documentos procesados.");

        // Calcular IDF + longitudes
        System.out.println("\nCALCULANDO IDF Y LONGITUDES...");
        Map<String, Double> longitudesDocs = calcularIDFyLongitudes(indiceInvertido, totalDocs);

        // Guardar ficheros
        System.out.println("GUARDANDO FICHEROS...");
        guardarIndice(indiceInvertido, indicePath);
        guardarLongitudes(longitudesDocs, longitudesPath);

        System.out.println("Terminos unicos: " + indiceInvertido.size());
        System.out.println("Documentos: " + totalDocs);
    }

    // FUNCIONES AUXILIARES

    private static String limpiarTexto(String texto){
        texto = texto.toLowerCase();    // normalizamos
        texto = texto.replaceAll("[^a-z0-9\\s-]", " "); // eliminar puntuacion
        texto = texto.replaceAll("\\b[0-9]+\\b", " "); // eliminar numeros
        texto = texto.replaceAll("\\s+", " "); // eliminar espacios multiples
        texto = texto.replaceAll("-+ | -+", " "); // eliminar guiones
        return texto;
    }

    // Dividimos el texto en terminos
    private static List<String> dividirenTerminos(String texto){
        return Arrays.asList(texto.split(" "));
    }

    private static List<String> filtrarTerminos(List<String> terminos, Set<String> stopwords){
        List<String> resultado = new ArrayList<>();
        for(Dtring termino : terminos){
            if(termino.length() < 3) continue;

            String stem = aplicarStemming(termino);

            if(stopwords.contains(stem)) continue;
            resultado.add(stem);
        }
        return resultado;
    }

    // Stemming
    private static String aplicarStemming(String termino){
        if(termino.endsWith("s")) termino = termino.substring(0, termino.length() - 1);
        if(termino.endsWith("ed")) termino = termino.substring(0, termino.length() - 2);
        if(termino.endsWith("es")) termino = termino.substring(0, termino.length() - 2);
        if(termino.endsWith("ing")) termino = termino.substring(0, termino.length() - 3);
        if(termino.endsWith("ies")) termino = termino.substring(0, termino.length() - 3) + "y";
        return termino;
    }

    // Contar frecuencias
    private static Map<String, Integer> contarFrecuencias(List<String> terminos){
        Map<String, Integer> frecuencias = new HashMap<>();
        for(String termino : terminos){
            frecuencias.put(termino, frecuencias.getOrDefault(termino, 0) + 1);
        }
    }

    // Calcular TF
    private static double calcularTF(int frecuencia){
        return 1 + Math.log(frecuencia) / Math.log(2);
    }

    
}
