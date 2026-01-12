import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Clase Indexacion
 * 
 * Esta clase implementa un sistema de indexación de documentos basado en la técnica TF-IDF
 * (Term Frequency - Inverse Document Frequency). Su propósito es:
 * 
 * 1. Procesar un corpus de documentos ubicados en la carpeta 'corpus'
 * 2. Limpiar y normalizar el texto de cada documento
 * 3. Crear un índice invertido que asocia términos con documentos
 * 4. Calcular los pesos TF-IDF para cada término en cada documento
 * 5. Guardar el índice invertido y las longitudes de documentos en archivos de salida
 * 
 * El índice invertido es una estructura de datos fundamental en recuperación de información
 * que permite búsquedas rápidas de documentos que contienen términos específicos.
 */
public class Indexacion{
    /**
     * Clase interna InfoTermino
     * 
     * Representa la información asociada a un término en el índice invertido.
     * Para cada término se almacena:
     * - idf: el valor IDF (Inverse Document Frequency) del término
     * - documentos: un mapa que asocia cada identificador de documento con su peso TF-IDF
     */
    static class InfoTermino{
        // Valor IDF (Inverse Document Frequency) del término
        // Indica qué tan único es el término en todo el corpus
        double idf = 0.0;
        
        // Mapa que almacena los documentos que contienen este término
        // Clave: identificador del documento (nombre del archivo)
        // Valor: peso TF-IDF del término en ese documento
        Map<String, Double> documentos = new HashMap<>();
    }

    public static void main(String[] args) {
        System.out.println("Iniciando el procesamiento de documentos...");
        try {
            // PASO 1: Ejecutar el crawler
            // Verifica si existe el corpus, si no, descarga los documentos
            ejecutarCrawler("corpus");
            
            // PASO 2: Crear el índice invertido
            // Procesa todos los documentos del corpus y calcula TF-IDF
            crearIndice("corpus", "output/index.txt", "output/lengths.txt");

            System.out.println("Indexacion completada");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            // Imprime la traza completa del error para facilitar depuración
            e.printStackTrace();
        }
    }

    /**
     * Método ejecutarCrawler
     * 
     * Verifica si el corpus de documentos existe y está completo.
     * Si no existe o está vacío, ejecuta el crawler para descargar los documentos.
     * El crawler es responsable de obtener los documentos del repositorio web
     * (en este caso, desde index.html).
     * 
     * @param Corpus Ruta de la carpeta que contiene los documentos
     * @throws IOException Si ocurre un error al acceder al sistema de archivos
     */
    private static void ejecutarCrawler(String Corpus) throws IOException, InterruptedException{
        // Obtener la ruta del corpus como objeto Path
        Path corpusPath = Paths.get(Corpus);
        
        // Verificar si la carpeta corpus existe
        if(!Files.exists(corpusPath)){
            System.out.println("La carpeta corpus no existe -> CREANDO...");
            // Si no existe, descargar los documentos desde la web
            descargarDocumentosWeb(Corpus);
            return;
        }

        // Verificar si la carpeta corpus existe pero está vacío
        if(!Files.list(corpusPath).findAny().isPresent()){
            System.out.println("El corpus está vacío -> DESCARGANDO...");
            // Si está vacía, descargar los documentos
            descargarDocumentosWeb(Corpus);
            return;
        }

        // Si el corpus ya existe y tiene contenido, no hacer nada
        System.out.println("La carpeta corpus ya existe -> descargar de la web");
        descargarDocumentosWeb(Corpus);
    }

    /**
     * Método descargarDocumentosWeb
     * 
     * Realiza la descarga de los documentos desde una fuente web (index.html).
     */
    private static void descargarDocumentosWeb(String carpetaCorpus) throws IOException, InterruptedException{
    Path corpusDir = Paths.get(carpetaCorpus);
    Files.createDirectories(corpusDir);

    System.out.println("Descargando documentos que faltan...");
    HttpClient client = HttpClient.newHttpClient();

    // 2. Descargar index.html
    String indexUrl = "https://raw.githubusercontent.com/andres-munoz/RECINF-Project/refs/heads/main/index.html";
    HttpRequest req = HttpRequest.newBuilder(URI.create(indexUrl)).build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    String html = resp.body();

    // 3. Buscar TODOS los <a href='corpus/NUMERO'>
    Pattern p;
        p = Pattern.compile("href='corpus/([0-9]+)'");
    Matcher m = p.matcher(html);

    int nuevos = 0;
    int yaExistian = 0;

    while (m.find()) {
        String idDoc = m.group(1);  // "000365356800057"
        Path archivoLocal = corpusDir.resolve(idDoc);

        // SI YA EXISTE, saltar
        if (Files.exists(archivoLocal)) {
            yaExistian++;
            continue;
        }

        // DESCARGAR
        String urlDoc = "https://raw.githubusercontent.com/andres-munoz/RECINF-Project/refs/heads/main/corpus/" + idDoc;
        HttpRequest reqDoc = HttpRequest.newBuilder(URI.create(urlDoc)).build();
        HttpResponse<byte[]> respDoc = client.send(reqDoc, HttpResponse.BodyHandlers.ofByteArray());
        
        Files.write(archivoLocal, respDoc.body());
        nuevos++;
        System.out.println("   ✅ " + idDoc);
    }

    System.out.println("   Crawler terminado:");
    System.out.println("   Nuevos: " + nuevos);
    System.out.println("   Ya existían: " + yaExistian);
    System.out.println("   Total en corpus: " + Files.list(corpusDir).count());
}

    /**
     * Método crearIndice
     * 
     * Este es el método principal que implementa todo el proceso de indexación TF-IDF.
     * Realiza los siguientes pasos:
     * 
     * 1. Carga los stopwords (palabras comunes sin significado)
     * 2. Itera sobre todos los documentos en el corpus
     * 3. Para cada documento:
     *    - Limpia y normaliza el texto
     *    - Divide el texto en términos
     *    - Filtra stopwords y aplica stemming
     *    - Calcula las frecuencias de los términos (TF)
     * 4. Calcula IDF para cada término
     * 5. Calcula la longitud (norma) de cada documento
     * 6. Guarda los resultados en archivos
     * 
     * @param Corpus Ruta de la carpeta con los documentos
     * @param indicePath Ruta del archivo de salida para el índice invertido
     * @param longitudesPath Ruta del archivo de salida para las longitudes
     * @throws IOException Si ocurre un error al leer/escribir archivos
     */
    public static void crearIndice(String Corpus, String indicePath, String longitudesPath) throws IOException{
        
        // FASE 1: CARGA DE STOPWORDS
        // Los stopwords son palabras muy comunes (el, la, de, en, etc.) que no aportan
        // significado semántico y deben ser filtradas antes del análisis
        System.out.println("\n CARGANDO STOPWORDS...");
        Set<String> stopwords = cargarStopwords("stopwords.txt");

        // FASE 2: PROCESAMIENTO DE DOCUMENTOS
        // Se itera sobre cada documento para extraer y procesar sus términos
        System.out.println("PROCESANDO DOCUMENTOS...");
        
        // Estructura principal: Índice invertido
        // Clave: término
        // Valor: información del término (IDF y documentos donde aparece)
        Map<String, InfoTermino> indiceInvertido = new HashMap<>();
        
        // Lista para mantener registro de los documentos procesados
        List<String> listaDocumentos = new ArrayList<>();

        // INFORMACIÓN INICIAL: Mostrar el contenido de la carpeta corpus
        // Esto ayuda a verificar qué documentos se van a procesar
        System.out.println("\nContenido de '" + Corpus + "':");

        // PROCESAMIENTO PRINCIPAL: Recorrer todos los documentos del corpus
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(Corpus))){
            for(Path archivo : stream){
                // Saltar si no es un archivo regular (ignorar directorios)
                if(!Files.isRegularFile(archivo)) continue;

                // Obtener el identificador del documento (nombre del archivo)
                String docId = archivo.getFileName().toString();
                listaDocumentos.add(docId);

                try {
                    // ========================================================
                    // PROCESAMIENTO DE UN DOCUMENTO INDIVIDUAL
                    // ========================================================
                    // Este bloque implementa el pipeline completo de procesamiento
                    // que se aplica a cada documento del corpus
                    
                    // PASO 1: LECTURA DEL DOCUMENTO
                    // ---------------------------------------------
                    // Lee todo el contenido del archivo como un array de bytes
                    // y lo convierte a String usando la codificación por defecto
                    // Files.readAllBytes() es eficiente y lee todo el archivo de una vez
                    String texto = new String(Files.readAllBytes(Paths.get(archivo.toString())));
                    // En este punto, 'texto' contiene el documento original sin procesar
                    // Procesar el documento
                    String textoLimpio = limpiarTexto(texto);   
                    List<String> terminos = dividirEnTerminos(textoLimpio);
                    List<String> terminosFiltrados = filtrarTerminos(terminos, stopwords);  // Filtro términos + Stemming
                    
                    // PASO 5: CÁLCULO DE FRECUENCIAS (TF - Term Frequency)
                    // ---------------------------------------------
                    // Cuenta cuántas veces aparece cada término único en el documento
                    // Ejemplo: ["cat", "dog", "cat"] → {"cat": 2, "dog": 1}
                    // Esta es la base para calcular el TF posteriormente
                    Map<String, Integer> frecuencias = contarFrecuencias(terminosFiltrados);
                    
                    // PASO 6: ACTUALIZACIÓN DEL ÍNDICE INVERTIDO
                    // ---------------------------------------------
                    // PASO 6: ACTUALIZACIÓN DEL ÍNDICE INVERTIDO
                    // ---------------------------------------------
                    // Procesar cada término único y su frecuencia en el documento
                    for(Map.Entry<String, Integer> entrada : frecuencias.entrySet()){
                        // Obtener el término y su frecuencia
                        String termino = entrada.getKey();  // Ej: "computer"
                        int frec = entrada.getValue();       // Ej: 5 (aparece 5 veces)
                        
                        // CALCULAR TF (Term Frequency) con logaritmo
                        // Fórmula: TF = 1 + log₂(frecuencia)
                        // Razón del logaritmo: Reduce el impacto de frecuencias muy altas
                        // Ejemplo: frec=1 → TF=1.0, frec=2 → TF=2.0, frec=4 → TF=3.0
                        // Sin log: frec=100 dominaría sobre frec=1
                        // Con log: frec=100 → TF≈7.64, más equilibrado
                        double tf = calcularTF(frec);

                        // ACTUALIZAR EL ÍNDICE INVERTIDO
                        // El índice invertido es un mapa: término → InfoTermino
                        // InfoTermino contiene: IDF y mapa de (docID → peso)
                        
                        // Si el término ya existe en el índice, obtenerlo
                        // Si es nuevo, crear una nueva entrada InfoTermino
                        // computeIfAbsent es atómico y eficiente
                        InfoTermino info = indiceInvertido.computeIfAbsent(termino, k -> new InfoTermino());
                        
                        // Almacenar el TF calculado para este documento
                        // Estructura: termino → {idf: X, documentos: {doc1: TF1, doc2: TF2, ...}}
                        // En este punto solo guardamos TF, el IDF se calcula después
                        info.documentos.put(docId, tf);
                        
                        // ESTADO ACTUAL DEL ÍNDICE:
                        // indiceInvertido = {
                        //   "computer": {idf: 0.0, documentos: {"doc1": 3.32, "doc2": 2.58}},
                        //   "science":  {idf: 0.0, documentos: {"doc1": 1.0}}
                        // }
                    }
                    
                    // Confirmar que el documento fue procesado exitosamente
                    //System.out.println(docId + " (" + terminosFiltrados.size() + " términos)");
                } catch (Exception e) {
                    // Capturar errores al procesar un documento específico
                    System.out.println(" Error procesando " + docId + ": " + e.getMessage());
                }
                
                // Mostrar progreso cada 360 documentos
                if(listaDocumentos.size() % 360 == 0){
                    System.out.println(listaDocumentos.size() + " documentos procesados...");
                }
            }
        }

        // Contar el número total de documentos procesados
        int totalDocs = listaDocumentos.size();
        System.out.println("Total: " + totalDocs + " documentos procesados.");

        // FASE 3: CÁLCULO DE IDF Y LONGITUDES DE DOCUMENTOS
        // IDF (Inverse Document Frequency) penaliza términos que aparecen en muchos documentos
        // Las longitudes se usan para normalizar vectores en búsquedas posteriores
        System.out.println("\nCALCULANDO IDF Y LONGITUDES...");
        Map<String, Double> longitudesDocs = calcularIDFyLongitudes(indiceInvertido, totalDocs);

        // FASE 4: GUARDADO DE RESULTADOS EN ARCHIVOS
        // Se almacenan el índice invertido y las longitudes para uso posterior en búsquedas
        System.out.println("GUARDANDO FICHEROS...");
        guardarIndice(indiceInvertido, indicePath);
        guardarLongitud(longitudesDocs, longitudesPath);

        // Mostrar estadísticas finales
        System.out.println("Terminos unicos: " + indiceInvertido.size());
        System.out.println("Documentos: " + totalDocs);
    }

    // ======================================================================
    // FUNCIONES AUXILIARES - Métodos que soportan el proceso de indexación
    // ======================================================================
    
    /**
     * Método filtrarTerminos
     * 
     * Filtra los términos eliminando stopwords y aplicando stemming.
     * 
     * @param terminos Lista de términos originales
     * @param stopwords Conjunto de stopwords a eliminar
     * @return Lista de términos filtrados y con stemming aplicado
     */
    private static String limpiarTexto(String texto){
        texto = texto.toLowerCase();    // normalizamos
        texto = texto.replaceAll("[^a-z0-9\\s-]", " "); // eliminar puntuacion
        texto = texto.replaceAll("\\b[0-9]+\\b", " "); // eliminar numeros
        texto = texto.replaceAll("\\s+", " "); // eliminar espacios multiples
        texto = texto.replaceAll("-+ | -+", " "); // eliminar guiones
        return texto;
    }

    /**
     * Método filtrarTerminos
     * 
     * Filtra los términos eliminando stopwords y aplicando stemming.
     * 
     * @param terminos Lista de términos originales
     * @param stopwords Conjunto de stopwords a eliminar
     * @return Lista de términos filtrados y con stemming aplicado
     */
    private static List<String> dividirEnTerminos(String texto){
        return Arrays.asList(texto.split(" "));
    }

    /**
     * Método filtrarTerminos
     * 
     * Filtra los términos eliminando stopwords y aplicando stemming.
     * 
     * @param terminos Lista de términos originales
     * @param stopwords Conjunto de stopwords a eliminar
     * @return Lista de términos filtrados y con stemming aplicado
     */
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

    /**
     * Método aplicarStemming
     * 
     * Aplica un stemming simple eliminando sufijos comunes.
     * Este método reduce las palabras a su raíz morfológica.
     * 
     * @param termino Término a procesar
     * @return Término con stemming aplicado
     */
    private static String aplicarStemming(String termino){
        if(termino.endsWith("s")) termino = termino.substring(0, termino.length() - 1);
        if(termino.endsWith("ed")) termino = termino.substring(0, termino.length() - 2);
        if(termino.endsWith("es")) termino = termino.substring(0, termino.length() - 2);
        if(termino.endsWith("ing")) termino = termino.substring(0, termino.length() - 3);
        if(termino.endsWith("ies")) termino = termino.substring(0, termino.length() - 3) + "y";
        return termino;
    }

    /**
     * Método contarFrecuencias
     * 
     * Cuenta cuántas veces aparece cada término en una lista.
     * Este es el cálculo del "Term Frequency" (frecuencia del término).
     * 
     * @param terminos Lista de términos de un documento
     * @return Mapa que asocia cada término con su frecuencia en el documento
     */
    private static Map<String, Integer> contarFrecuencias(List<String> terminos){
        Map<String, Integer> frecuencias = new HashMap<>();
        
        for(String termino : terminos){
            // Incrementar el contador para este término en 1
            // Si no existe, iniciar en 1 usando getOrDefault
            frecuencias.put(termino, frecuencias.getOrDefault(termino, 0) + 1);
        }
        return frecuencias;
    }

    /**
     * Método calcularTF
     * 
     * Calcula el valor TF (Term Frequency) usando logaritmo.
     * La fórmula utilizada es: TF = 1 + log2(frecuencia)
     * 
     * El logaritmo reduce el impacto de frecuencias muy altas, evitando que
     * palabras frecuentes en documentos largos dominen la relevancia.
     * El +1 asegura que TF > 0 incluso para frecuencia = 1.
     * 
     * @param frecuencia Número de veces que aparece el término en el documento
     * @return Valor TF calculado
     */
    private static double calcularTF(int frecuencia){
        // Fórmula: TF = 1 + log2(frecuencia)
        // Math.log(x) / Math.log(2) calcula log en base 2
        return 1 + Math.log(frecuencia) / Math.log(2);
    }

    /**
     * Método calcularIDFyLongitudes
     * 
     * Realiza dos tareas fundamentales:
     * 
     * 1. CALCULAR IDF (Inverse Document Frequency):
     *    Fórmula: IDF = log(N / df)
     *    - N = número total de documentos
     *    - df = número de documentos donde aparece el término
     *    - IDF es alta para términos raros y baja para términos comunes
     * 
     * 2. CALCULAR PESO TF-IDF Y LONGITUDES:
     *    - Peso = TF * IDF (combinación de relevancia local y global)
     *    - Longitud = sqrt(suma de todos los pesos al cuadrado)
     *    - La longitud se usa para normalizar vectores en búsquedas
     * 
     * @param indice Índice invertido con TF calculado
     * @param totalDocs Número total de documentos en el corpus
     * @return Mapa con la longitud normalizada de cada documento
     */
    private static Map<String, Double> calcularIDFyLongitudes(Map<String, InfoTermino> indice, int totalDocs){
        // ========================================================================
        // PROPÓSITO: Este método realiza DOS cálculos fundamentales en UN solo recorrido
        // 1. Calcular IDF (Inverse Document Frequency) para cada término
        // 2. Calcular la longitud (norma euclidiana) de cada documento
        // ========================================================================
        
        // Mapa para almacenar la longitud de cada documento
        // Clave: ID del documento, Valor: suma de (peso_término)²
        // Se calcula la raíz cuadrada al final
        Map<String, Double> longitudes = new HashMap<>();

        // RECORRIDO PRINCIPAL: Iterar sobre cada término del vocabulario
        // (todos los términos únicos encontrados en todo el corpus)
        for(Map.Entry<String, InfoTermino> entrada : indice.entrySet()){
            InfoTermino info = entrada.getValue();

            // ===================================================================
            // PASO 1: CALCULAR IDF (Inverse Document Frequency)
            // ===================================================================
            // IDF mide qué tan único/raro es un término en el corpus
            // 
            // FÓRMULA: IDF = log(N / df)
            // Donde:
            //   N  = número total de documentos en el corpus
            //   df = document frequency (documentos que contienen el término)
            // 
            // INTERPRETACIÓN:
            // - IDF alto   → término raro/específico (aparece en pocos docs)
            // - IDF bajo   → término común (aparece en muchos docs)
            // 
            // EJEMPLO:
            // - "computer" aparece en 10 de 100 docs → IDF = log(100/10) = 2.3 (relevante)
            // - "the" aparece en 95 de 100 docs      → IDF = log(100/95) = 0.05 (poco relevante)
            
            int df = info.documentos.size();  // Contar en cuántos docs aparece
            double idf = Math.log((double) totalDocs / df);  // Calcular IDF
            info.idf = idf;  // Almacenar IDF en la estructura para uso posterior

            // ===================================================================
            // PASO 2: CALCULAR PESO TF-IDF Y ACUMULAR PARA LONGITUD
            // ===================================================================
            // Para cada documento que contiene este término:
            for(Map.Entry<String, Double> posting : info.documentos.entrySet()){
                String docId = posting.getKey();      // ID del documento
                double tf = posting.getValue();       // TF previamente calculado
                
                // CALCULAR PESO TF-IDF
                // Peso = TF × IDF
                // Combina relevancia local (TF) con relevancia global (IDF)
                // - TF alto + IDF alto → término muy importante
                // - TF alto + IDF bajo → término frecuente pero común
                double peso = tf * idf;
                
                // ACUMULAR CUADRADO DEL PESO PARA CALCULAR LONGITUD
                // Fórmula de longitud: ||d|| = √(Σ peso²)
                // Esto es la norma euclidiana del vector documento
                // 
                // ¿Por qué peso²?
                // Para calcular la norma L2: √(w₁² + w₂² + ... + wₙ²)
                // Donde cada w es el peso TF-IDF de un término
                double suma = longitudes.getOrDefault(docId, 0.0);  // Obtener suma actual
                longitudes.put(docId, suma + peso * peso);          // Añadir peso²
                
                // NOTA: No calculamos √ aquí porque es más eficiente hacerlo
                // una vez al final, después de acumular todos los términos
            }
        }

        // ===================================================================
        // PASO 3: CALCULAR LA RAÍZ CUADRADA (FINALIZAR LONGITUD)
        // ===================================================================
        // En este punto, 'longitudes' contiene la suma de cuadrados: Σ(peso²)
        // Aplicamos √ para obtener la norma euclidiana final
        // 
        // FÓRMULA COMPLETA: ||d⃗ⱼ|| = √(Σᵢ wᵢ,ⱼ²)
        // Donde:
        //   wᵢ,ⱼ = peso TF-IDF del término i en el documento j
        // 
        // USO DE LA LONGITUD:
        // Se usa para normalizar documentos en búsquedas (similaridad de coseno)
        // cos(q,d) = (q·d) / (||q|| × ||d||)
        // 
        // replaceAll: Operación funcional que aplica √ a cada valor del mapa
        longitudes.replaceAll((docId, suma) -> Math.sqrt(suma));
        
        // RESULTADO FINAL:
        // longitudes = {"doc1": 45.23, "doc2": 38.91, ...}
        // Cada valor representa la "longitud" del vector documento en el espacio TF-IDF
        return longitudes;
    }


    /**
     * Método cargarStopwords
     * 
     * Carga la lista de stopwords desde un archivo de texto.
     * Los stopwords son palabras muy comunes que típicamente no aportan significado
     * semántico (artículos, preposiciones, conjunciones, etc.)
     * 
     * Cada línea del archivo debe contener un stopword.
     * Las líneas vacías se ignoran automáticamente.
     * 
     * @param ruta Ruta del archivo con los stopwords
     * @return Conjunto (Set) de stopwords cargados
     * @throws IOException Si ocurre un error al leer el archivo
     */
    private static Set<String> cargarStopwords(String ruta) throws IOException{
        Set<String> stopwords = new HashSet<>();
        
        // Leer todas las líneas del archivo
        Files.lines(Paths.get(ruta))
            // Remover espacios en blanco al inicio y final
            .map(String::trim)
            // Filtrar líneas vacías
            .filter(s -> !s.isEmpty())
            // Agregar cada línea al conjunto
            .forEach(stopwords::add);
        
        return stopwords;
    }

    /**
     * Método guardarIndice
     * 
     * Escribe el índice invertido en un archivo de texto con formato:
     * término | idf | (docId1 peso1) (docId2 peso2) ...
     * 
     * Ejemplo de línea:
     * algoritmo | 4.234 | (000084578400037 2.156) (000084578400038 1.892) ...
     * 
     * Este formato permite recuperar rápidamente todos los documentos
     * que contienen un término específico y sus pesos TF-IDF.
     * 
     * @param indice Índice invertido a guardar
     * @param ruta Ruta del archivo de salida
     * @throws IOException Si ocurre un error al escribir el archivo
     */
    private static void guardarIndice(Map<String, InfoTermino> indice, String ruta) throws IOException{
        // ========================================================================
        // PROPÓSITO: Persistir el índice invertido en un archivo de texto
        // ========================================================================
        // El índice invertido es la estructura fundamental para búsquedas rápidas.
        // Se guarda en formato texto para:
        // - Portabilidad: Legible por humanos y otras herramientas
        // - Debugging: Fácil inspección del contenido
        // - Compatibilidad: No depende de serialización Java
        
        // PASO 1: Crear directorios necesarios
        // Si no existe la carpeta 'output', se crea automáticamente
        Files.createDirectories(Paths.get(ruta).getParent());
        
        // PASO 2: Abrir archivo para escritura
        // try-with-resources asegura cierre automático incluso si hay excepciones
        // BufferedWriter es eficiente para escritura de texto
        try(BufferedWriter writer = Files.newBufferedWriter(Paths.get(ruta))){
            
            // PASO 3: Escribir cada término del índice
            // Iterar sobre todos los términos del vocabulario
            for(Map.Entry<String, InfoTermino> entrada : indice.entrySet()){
                String termino = entrada.getKey();      // Ej: "computer"
                InfoTermino info = entrada.getValue();  // {idf, documentos}
                
                // ===================================================================
                // FORMATO DE LÍNEA: término | idf | (doc1 peso1) (doc2 peso2) ...
                // ===================================================================
                // EJEMPLO:
                // computer | 2.345 | (000084578400037 3.521) (000084578400038 2.103)
                // 
                // COMPONENTES:
                // 1. término: Palabra clave indexada
                // 2. idf: Inverse Document Frequency (importancia global)
                // 3. postings: Lista de (docID, peso_TF-IDF) donde aparece
                // 
                // Este formato permite:
                // - Recuperar rápidamente IDF del término
                // - Obtener todos los documentos que contienen el término
                // - Conocer el peso TF-IDF de cada ocurrencia
                
                // Construir inicio de línea: "término | idf | "
                StringBuilder linea = new StringBuilder(
                    termino + " | " + 
                    String.format("%.3f", info.idf) +  // 3 decimales para IDF
                    " | "
                );
                
                // Agregar los postings (pares documento-peso)
                for(Map.Entry<String, Double> posting : info.documentos.entrySet()){
                    String docId = posting.getKey();
                    double tf = posting.getValue();
                    // Calcular el peso final TF-IDF
                    double peso = tf * info.idf;
                    // Formato: (docId peso)
                    linea.append("(").append(docId).append(" ").append(String.format("%.3f", peso)).append(") ");
                }
                
                // Escribir la línea en el archivo
                writer.write(linea.toString().trim());
                writer.newLine();
            }
        }
    }

    /**
     * Método guardarLongitud
     * 
     * Guarda la longitud (norma euclidiana) de cada documento en un archivo.
     * Formato: docId longitud
     * 
     * Ejemplo:
     * 000084578400037 125.456
     * 000084578400038 98.234
     * 
     * Estas longitudes se usan para normalizar vectores durante búsquedas
     * para calcular similaridad de coseno entre documentos y consultas.
     * 
     * @param longitudes Mapa con la longitud de cada documento
     * @param ruta Ruta del archivo de salida
     * @throws IOException Si ocurre un error al escribir el archivo
     */
    private static void guardarLongitud(Map<String, Double> longitudes, String ruta) throws IOException{
        // Crear los directorios padre si no existen
        Files.createDirectories(Paths.get(ruta).getParent());
        
        // Abrir archivo para escritura con try-with-resources
        try(BufferedWriter writer = Files.newBufferedWriter(Paths.get(ruta))){
            // Iterar sobre cada documento y su longitud
            for(Map.Entry<String, Double> entrada : longitudes.entrySet()){
                // Escribir en formato: docId longitud
                writer.write(entrada.getKey() + " " + String.format("%.3f", entrada.getValue()));
                writer.newLine();
            }
        }
    }
}