import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Indexacion{
    // Clase InfoTermino: representa la información asociada a un término en el índice invertido.
    static class InfoTermino{
        double idf = 0.0;
        
        // Mapa que almacena los documentos que contienen este término
        Map<String, Double> documentos = new HashMap<>();
    }

    public static void main(String[] args) {
        System.out.println("Iniciando el procesamiento de documentos...");
        try {
            // PASO 1: Ejecutar el crawler
            ejecutarCrawler("corpus");
            
            // PASO 2: Crear el índice invertido
            crearIndice("corpus", "output/index.txt", "output/lengths.txt");

            System.out.println("Indexacion completada");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método ejecutarCrawler
    private static void ejecutarCrawler(String Corpus) throws IOException, InterruptedException{
        Path corpusPath = Paths.get(Corpus);
        
        // Verificar si la carpeta corpus no existe
        if(!Files.exists(corpusPath)){
            System.out.println("La carpeta corpus no existe -> CREANDO...");
            descargarDocumentosWeb(Corpus);
            return;
        }

        // Verificar si la carpeta corpus existe pero está vacío
        if(!Files.list(corpusPath).findAny().isPresent()){
            System.out.println("El corpus está vacío -> DESCARGANDO...");
            descargarDocumentosWeb(Corpus);
            return;
        }

        // Si el corpus ya existe y tiene contenido, de todas formas descarga de la web
        System.out.println("La carpeta corpus ya existe -> descargar de la web");
        descargarDocumentosWeb(Corpus);
    }

    // Método descargarDocumentosWeb: descarga los documentos de index.html
    private static void descargarDocumentosWeb(String carpetaCorpus) throws IOException, InterruptedException{
    Path corpusDir = Paths.get(carpetaCorpus);
    Files.createDirectories(corpusDir);

    System.out.println("Descargando documentos que faltan...");
    HttpClient client = HttpClient.newHttpClient();

    String indexUrl = "https://raw.githubusercontent.com/andres-munoz/RECINF-Project/refs/heads/main/index.html";
    HttpRequest req = HttpRequest.newBuilder(URI.create(indexUrl)).build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    String html = resp.body();

    // Busca todos los <a href='corpus/NUMERO'>
    Pattern p;
        p = Pattern.compile("href='corpus/([0-9]+)'");
    Matcher m = p.matcher(html);

    int nuevos = 0;
    int yaExistian = 0;

    while (m.find()) {
        String idDoc = m.group(1);  // "000365356800057"
        Path archivoLocal = corpusDir.resolve(idDoc);

        if (Files.exists(archivoLocal)) {
            yaExistian++;
            continue;
        }

        String urlDoc = "https://raw.githubusercontent.com/andres-munoz/RECINF-Project/refs/heads/main/corpus/" + idDoc;
        HttpRequest reqDoc = HttpRequest.newBuilder(URI.create(urlDoc)).build();
        HttpResponse<byte[]> respDoc = client.send(reqDoc, HttpResponse.BodyHandlers.ofByteArray());
        
        Files.write(archivoLocal, respDoc.body());
        nuevos++;
        System.out.println(idDoc);
    }

    System.out.println("   Crawler terminado:");
    System.out.println("   Nuevos: " + nuevos);
    System.out.println("   Ya existían: " + yaExistian);
    System.out.println("   Total en corpus: " + Files.list(corpusDir).count());
}

    //Método crearIndice: implementa todo el proceso de indexación TF-IDF.
    public static void crearIndice(String Corpus, String indicePath, String longitudesPath) throws IOException{
        
        System.out.println("\n CARGANDO STOPWORDS...");
        Set<String> stopwords = cargarStopwords("stopwords.txt");

        System.out.println("PROCESANDO DOCUMENTOS...");
        
        Map<String, InfoTermino> indiceInvertido = new HashMap<>();
        
        List<String> listaDocumentos = new ArrayList<>();

        System.out.println("\nContenido de '" + Corpus + "':");

        //Recorre todos los documentos del corpus
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(Corpus))){
            for(Path archivo : stream){
                if(!Files.isRegularFile(archivo)) continue;

                String docId = archivo.getFileName().toString();
                listaDocumentos.add(docId);

                try {
                    // ========================================================
                    // PROCESAMIENTO DE UN DOCUMENTO INDIVIDUAL
                    // ========================================================

                    String texto = new String(Files.readAllBytes(Paths.get(archivo.toString())));
                
                    String textoLimpio = limpiarTexto(texto);   
                    List<String> terminos = dividirEnTerminos(textoLimpio);
                    List<String> terminosFiltrados = filtrarTerminos(terminos, stopwords);  // Filtro términos + Stemming
                    
                    // ========================================================
                    // CÁCULO DE FRECUENCIAS DE TÉRMINOS
                    // ========================================================
                    Map<String, Integer> frecuencias = contarFrecuencias(terminosFiltrados);
                    
                    for(Map.Entry<String, Integer> entrada : frecuencias.entrySet()){
                        String termino = entrada.getKey();
                        int frec = entrada.getValue();
                        
                        // CALCULAR TF (Term Frequency) con logaritmo
                        // Fórmula: TF = 1 + log₂(frecuencia)
                        double tf = calcularTF(frec);

                        // ACTUALIZAR EL ÍNDICE INVERTIDO
                        // El índice invertido es un mapa: término → InfoTermino
                        // InfoTermino contiene: IDF y mapa de (docID → peso)
                        InfoTermino info = indiceInvertido.computeIfAbsent(termino, k -> new InfoTermino());
                        
                        info.documentos.put(docId, tf);
                    }
                } catch (Exception e) {
                    System.out.println(" Error procesando " + docId + ": " + e.getMessage());
                }
                
                // Mostrar progreso cada 360 documentos
                if(listaDocumentos.size() % 360 == 0){
                    System.out.println(listaDocumentos.size() + " documentos procesados...");
                }
            }
        }

        int totalDocs = listaDocumentos.size();
        System.out.println("Total: " + totalDocs + " documentos procesados.");

        //CÁLCULO DE IDF Y LONGITUDES DE DOCUMENTOS
        System.out.println("\nCALCULANDO IDF Y LONGITUDES...");
        Map<String, Double> longitudesDocs = calcularIDFyLongitudes(indiceInvertido, totalDocs);

        System.out.println("GUARDANDO FICHEROS...");
        guardarIndice(indiceInvertido, indicePath);
        guardarLongitud(longitudesDocs, longitudesPath);

        System.out.println("Terminos unicos: " + indiceInvertido.size());
        System.out.println("Documentos: " + totalDocs);
    }

    // ======================================
    // FUNCIONES AUXILIARES
    // ======================================
    
    // Método limpiarTexto: limpia el texto eliminando caracteres especiales, números y normalizando.
    private static String limpiarTexto(String texto){
        texto = texto.toLowerCase();    // normalizamos
        texto = texto.replaceAll("[^a-z0-9\\s-]", " "); // eliminar caracteres especiales
        texto = texto.replaceAll("\\b[0-9]+\\b", " "); // eliminar numeros
        texto = texto.replaceAll("\\s+", " "); // eliminar espacios multiples
        texto = texto.replaceAll("-+ | -+", " "); // eliminar guiones
        return texto;
    }

    // Método dividirEnTerminos: divide el texto en términos usando espacios como separadores.
    private static List<String> dividirEnTerminos(String texto){
        return Arrays.asList(texto.split(" "));
    }

    // Método filtrarTerminos: filtra términos cortos y stopwords, aplicando stemming.
    private static List<String> filtrarTerminos(List<String> terminos, Set<String> stopwords){
        List<String> resultado = new ArrayList<>();
        for(String termino : terminos){
            if(termino.length() < 3) continue;

            String stem = aplicarStemming(termino);

            if(stopwords.contains(stem)) continue;
            resultado.add(stem);
        }
        return resultado;
    }

    // Método aplicarStemming: aplica un stemming simple eliminando sufijos comunes.
    private static String aplicarStemming(String termino){
        if(termino.endsWith("s")) termino = termino.substring(0, termino.length() - 1);
        if(termino.endsWith("ed")) termino = termino.substring(0, termino.length() - 2);
        if(termino.endsWith("es")) termino = termino.substring(0, termino.length() - 2);
        if(termino.endsWith("ing")) termino = termino.substring(0, termino.length() - 3);
        if(termino.endsWith("ies")) termino = termino.substring(0, termino.length() - 3) + "y";
        return termino;
    }

    // Método contarFrecuencias: cuenta cuántas veces aparece cada término en una lista.
    private static Map<String, Integer> contarFrecuencias(List<String> terminos){
        Map<String, Integer> frecuencias = new HashMap<>();
        
        for(String termino : terminos){
            // Incrementar el contador para este término en 1
            // Si no existe, iniciar en 1 usando getOrDefault
            frecuencias.put(termino, frecuencias.getOrDefault(termino, 0) + 1);
        }
        return frecuencias;
    }

    // Calcula el valor TF (Term Frequency) usando logaritmo.
    private static double calcularTF(int frecuencia){
        // Fórmula: TF = 1 + log2(frecuencia)
        // Math.log(x) / Math.log(2) calcula log en base 2
        return 1 + Math.log(frecuencia) / Math.log(2);
    }

    // Método calcularIDFyLongitudes
    // 1.CALCULAR IDF: Fórmula: IDF = log(N / df)
    // 2.CALCULAR TF-IDF Y LONGITUDES: 
    // Fórmula de Peso = TF * IDF (combinación de relevancia local y global)
    // Fórmula de longitud: ||d|| = √(Σ peso²)
    private static Map<String, Double> calcularIDFyLongitudes(Map<String, InfoTermino> indice, int totalDocs){
        Map<String, Double> longitudes = new HashMap<>();
        for(Map.Entry<String, InfoTermino> entrada : indice.entrySet()){
            InfoTermino info = entrada.getValue();

            // ===================================================================
            // PASO 1: CALCULAR IDF (Inverse Document Frequency)
            // ===================================================================
            
            int df = info.documentos.size();  // Contar en cuántos docs aparece
            double idf = Math.log((double) totalDocs / df);  // Calcular IDF
            info.idf = idf;  // Almacenar IDF en la estructura para uso posterior

            // ===================================================================
            // PASO 2: CALCULAR PESO TF-IDF Y ACUMULAR PARA LONGITUD
            // ===================================================================

            for(Map.Entry<String, Double> posting : info.documentos.entrySet()){
                String docId = posting.getKey(); 
                double tf = posting.getValue();      
                double peso = tf * idf;
                
                double suma = longitudes.getOrDefault(docId, 0.0);
                longitudes.put(docId, suma + peso * peso);       
            }
        }

        // ===================================================================
        // PASO 3: CALCULAR LA RAÍZ CUADRADA (FINALIZAR LONGITUD)
        // ===================================================================

        longitudes.replaceAll((docId, suma) -> Math.sqrt(suma));
        
        return longitudes;
    }


    // Método cargarStopwords: carga las stopwords desde un archivo de texto.
    private static Set<String> cargarStopwords(String ruta) throws IOException{
        Set<String> stopwords = new HashSet<>();
        
        Files.lines(Paths.get(ruta))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(stopwords::add);
        
        return stopwords;
    }

    // Método guardarIndice: guarda el índice invertido en un archivo de texto.
    private static void guardarIndice(Map<String, InfoTermino> indice, String ruta) throws IOException{
        Files.createDirectories(Paths.get(ruta).getParent());
        
        try(BufferedWriter writer = Files.newBufferedWriter(Paths.get(ruta))){
            for(Map.Entry<String, InfoTermino> entrada : indice.entrySet()){
                String termino = entrada.getKey();      // Ej: "computer"
                InfoTermino info = entrada.getValue();  // {idf, documentos}
                
                // ===================================================================
                // FORMATO DE LÍNEA: término | idf | (doc1 peso1) (doc2 peso2) ...
                // ===================================================================
            
                StringBuilder linea = new StringBuilder(
                    termino + " | " + 
                    String.format("%.3f", info.idf) +
                    " | "
                );
                
                for(Map.Entry<String, Double> posting : info.documentos.entrySet()){
                    String docId = posting.getKey();
                    double tf = posting.getValue();
                    double peso = tf * info.idf;
                    linea.append("(").append(docId).append(" ").append(String.format("%.3f", peso)).append(") ");
                }
                
                writer.write(linea.toString().trim());
                writer.newLine();
            }
        }
    }

    // Método guardarLongitud: guarda las longitudes de los documentos en un archivo de texto.
    private static void guardarLongitud(Map<String, Double> longitudes, String ruta) throws IOException{
        Files.createDirectories(Paths.get(ruta).getParent());
        
        try(BufferedWriter writer = Files.newBufferedWriter(Paths.get(ruta))){
            for(Map.Entry<String, Double> entrada : longitudes.entrySet()){
                // Escribir en formato: docId | longitud
                writer.write(entrada.getKey() + " | " + String.format("%.3f", entrada.getValue()));
                writer.newLine();
            }
        }
    }
}