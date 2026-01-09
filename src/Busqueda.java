import java.io.*;
import java.util.*;
import java.nio.file.*;

public class Busqueda {
    // estructura que contendrá la información del documento
    private static class ResultadoDocumento {
        String nombreDocumento;
        double puntuacion;

        ResultadoDocumento(String nombreDocumento, double puntuacion) {
            this.nombreDocumento = nombreDocumento;
            this.puntuacion = puntuacion;
        }
    }

    private Scanner scanner = new Scanner(System.in);
    private Map<String, Map<String, Object>> indiceInvertido;
    private Map<String, Double> longitudesDocumentos;

    // método principal para iniciar el motor de búsqueda
    public static void main(String[] args) throws IOException {
        Busqueda busqueda = new Busqueda();
        busqueda.cargarIndiceDesdeTxt("output/indice.txt", "output/longitudes.txt");
        busqueda.start();
    }

    // método para cargar el índice desde archivos .txt
    public void cargarIndiceDesdeTxt(String rutaIndiceTxt, String rutaLongitudesTxt) throws IOException {
        convertirIndiceTxtAJson(rutaIndiceTxt, "src/main/resources/indice_invertido.json");
        convertirLongitudesTxtAJson(rutaLongitudesTxt, "src/main/resources/longitudes_documentos.json");
        cargarIndice("src/main/resources/indice_invertido.json", "src/main/resources/longitudes_documentos.json");
    }

    // método para convertir indice.txt a indice_invertido.json
    private static void convertirIndiceTxtAJson(String rutaEntrada, String rutaSalida) throws IOException {
        Map<String, Map<String, Object>> indiceInvertido = new HashMap<>();
        List<String> lineas = Files.readAllLines(Paths.get(rutaEntrada));

        for (String linea : lineas) {
            String[] partes = linea.split("\\|");
            if (partes.length < 2) continue;

            String termino = partes[0].trim();
            double idf = Double.parseDouble(partes[1].trim());

            Map<String, Object> datosTermino = new HashMap<>();
            datosTermino.put("idf", idf);

            Map<String, Double> pesosDocumentos = new HashMap<>();
            if (partes.length > 2) {
                String pesosDocumentosStr = partes[2].trim();
                String[] entradasDocumentos = pesosDocumentosStr.split("\\)\\s*\\(|[()]");
                for (String entrada : entradasDocumentos) {
                    if (entrada.trim().isEmpty()) continue;
                    String[] pesoDocumento = entrada.trim().split("\\s+");
                    if (pesoDocumento.length == 2) {
                        String idDocumento = pesoDocumento[0];
                        double peso = Double.parseDouble(pesoDocumento[1]);
                        pesosDocumentos.put(idDocumento, peso);
                    }
                }
            }
            datosTermino.put("pesosDocumentos", pesosDocumentos);
            indiceInvertido.put(termino, datosTermino);
        }

        // guardar como JSON
        JSONObject indiceJson = new JSONObject(indiceInvertido);
        Files.write(Paths.get(rutaSalida), indiceJson.toString(2).getBytes());
    }

    // método para convertir longitudes.txt a longitudes_documentos.json
    private static void convertirLongitudesTxtAJson(String rutaEntrada, String rutaSalida) throws IOException {
        Map<String, Double> longitudesDocumentos = new HashMap<>();
        List<String> lineas = Files.readAllLines(Paths.get(rutaEntrada));

        for (String linea : lineas) {
            String[] partes = linea.trim().split("\\s+");
            if (partes.length == 2) {
                String idDocumento = partes[0];
                double longitud = Double.parseDouble(partes[1]);
                longitudesDocumentos.put(idDocumento, longitud);
            }
        }

        // guardar como JSON
        JSONObject longitudesJson = new JSONObject(longitudesDocumentos);
        Files.write(Paths.get(rutaSalida), longitudesJson.toString(2).getBytes());
    }

    // método para cargar el índice y las longitudes desde archivos JSON
    public void cargarIndice(String rutaIndice, String rutaLongitudes) {
        try {
            // cargaríndice invertido desde JSON
            String contenidoIndice = new String(Files.readAllBytes(Paths.get(rutaIndice)));
            JSONObject indiceJson = new JSONObject(contenidoIndice);
            indiceInvertido = new HashMap<>();
            for (String termino : indiceJson.keySet()) {
                JSONObject datosTermino = indiceJson.getJSONObject(termino);
                Map<String, Object> datos = new HashMap<>();
                datos.put("idf", datosTermino.getDouble("idf"));
                datos.put("pesosDocumentos", datosTermino.getJSONObject("docWeights").toMap());
                indiceInvertido.put(termino, datos);
            }

            // cargarlongitudes de documentos desde JSON
            String contenidoLongitudes = new String(Files.readAllBytes(Paths.get(rutaLongitudes)));
            JSONObject longitudesJson = new JSONObject(contenidoLongitudes);
            longitudesDocumentos = new HashMap<>();
            for (String documento : longitudesJson.keySet()) {
                longitudesDocumentos.put(documento, longitudesJson.getDouble(documento));
            }
        } catch (Exception e) {
            System.err.println("Error al cargar el índice: " + e.getMessage());
        }
    }

    // comienza el motor de búsqueda interactivo
    public void start() {
        System.out.println("\nMotor de búsqueda:");

        while (true) {
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

    // método para la búsqueda de un término
    private void busquedaTermino() {
        System.out.print("Introduce el término a buscar: ");
        String consulta = scanner.nextLine().trim();
        String terminoP = procesarTermino(consulta);
        if (terminoP.isEmpty()) {
            System.out.println("No se encontraron documentos relevantes.");
            return;
        }
        List<String> terminos = Collections.singletonList(terminoP);
        Set<String> documentosRelevantes = recuperarDocumentos(terminos);
        List<ResultadoDocumento> resultados = calcularRanking(terminos, documentosRelevantes);
        mostrarResultados(resultados, consulta);
    }

    // método para la búsqueda con operadores
    private void busquedaOperadores() {
        System.out.print("Introduce la consulta con operadores (and/or): ");
        String consulta = scanner.nextLine().trim();
        Map<String, List<String>> consultaAnalizada = analizarConsultaConOperadores(consulta);
        Set<String> documentosRelevantes = recuperarDocumentosConOperadores(consultaAnalizada);
        List<String> todosTerminos = new ArrayList<>();
        todosTerminos.addAll(consultaAnalizada.get("AND"));
        todosTerminos.addAll(consultaAnalizada.get("OR"));
        List<ResultadoDocumento> resultados = calcularRanking(todosTerminos, documentosRelevantes);
        mostrarResultados(resultados, consulta);
    }

    // método para la búsqueda de una frase
    private void busquedaFrase() {
        System.out.print("Introduce la frase a buscar (ej: \"índice invertido\"): ");
        String consulta = scanner.nextLine().trim();
        String[] terminos = consulta.toLowerCase().split("\\s+");
        List<String> terminosProcesados = procesarConsulta(consulta);
        Set<String> documentosRelevantes = recuperarDocumentosFrase(terminos);
        List<ResultadoDocumento> resultados = calcularRanking(terminosProcesados, documentosRelevantes);
        mostrarResultados(resultados, consulta);
    }

    // método para procesar un solo término
    private String procesarTermino(String consulta) {
        System.out.println("Consulta original: " + consulta);
        String termino = consulta;
        termino = termino.toLowerCase();
        termino = termino.replaceAll("[^a-z0-9\\s-]", " ");
        termino = termino.replaceAll("\\b[0-9]+\\b", " ");
        termino = termino.replaceAll("\\s+", " ");
        termino = termino.replaceAll("-+ | -+", " ");
        termino = termino.trim();

        if (termino.length() < 3) {
            System.out.println("Consulta procesada: (vacía, término demasiado corto)");
            return "";
        }

        try {
            Set<String> stopwords = cargarStopwords("stopwords.txt");
            termino = aplicarStemming(termino);
            if (stopwords.contains(termino)) {
                System.out.println("Consulta procesada: (vacía, término es stopword)");
                return "";
            }
        } catch (IOException e) {
            System.err.println("Error al cargar stopwords: " + e.getMessage());
        }

        System.out.println("Consulta procesada: " + termino);
        return termino;
    }

    // método para procesar una consulta de varios términos
    private List<String> procesarConsulta(String consulta) {
        System.out.println("Consulta original: " + consulta);
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

        System.out.print("Consulta procesada: ");
        for (String termino : terminosProcesados) {
            System.out.print(termino + " ");
        }
        System.out.println();
        return terminosProcesados;
    }

    // método para aplicar el stemming
    private String aplicarStemming(String termino) {
        if (termino.endsWith("s")) termino = termino.substring(0, termino.length() - 1);
        if (termino.endsWith("ed")) termino = termino.substring(0, termino.length() - 2);
        if (termino.endsWith("es")) termino = termino.substring(0, termino.length() - 2);
        if (termino.endsWith("ing")) termino = termino.substring(0, termino.length() - 3);
        if (termino.endsWith("ies")) termino = termino.substring(0, termino.length() - 3) + "y";
        return termino;
    }

    // método para cargar stopwords
    private Set<String> cargarStopwords(String ruta) throws IOException {
        Set<String> stopwords = new HashSet<>();
        try {
            Files.lines(Paths.get(ruta))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(stopwords::add);
        } catch (NoSuchFileException e) {
            System.out.println("Advertencia: No se encontró el archivo stopwords.txt. Usando lista vacía.");
        }
        return stopwords;
    }

    // método para analizar la consulta con operadores
    private Map<String, List<String>> analizarConsultaConOperadores(String consulta) {
        Map<String, List<String>> consultaAnalizada = new HashMap<>();
        consultaAnalizada.put("AND", new ArrayList<>());
        consultaAnalizada.put("OR", new ArrayList<>());

        String[] partes = consulta.toLowerCase().split("\\s+");
        String operadorActual = "OR";

        for (String parte : partes) {
            if (parte.equals("and")) {
                operadorActual = "AND";
            } else if (parte.equals("or")) {
                operadorActual = "OR";
            } else {
                String terminoProcesado = procesarTermino(parte);
                if (!terminoProcesado.isEmpty()) {
                    consultaAnalizada.get(operadorActual).add(terminoProcesado);
                }
            }
        }
        return consultaAnalizada;
    }

    // método para recuperar documentos relevantes para términos sueltos
    private Set<String> recuperarDocumentos(List<String> terminos) {
        Set<String> documentos = new HashSet<>();
        if (indiceInvertido == null) {
            System.out.println("Error: El índice invertido no está cargado.");
            return documentos;
        }

        for (String termino : terminos) {
            if (indiceInvertido.containsKey(termino)) {
                Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(termino).get("pesosDocumentos");
                documentos.addAll(pesosDocumentos.keySet());
            }
        }
        return documentos;
    }

    // método para recuperar documentos para operadores AND/OR
    private Set<String> recuperarDocumentosConOperadores(Map<String, List<String>> consultaAnalizada) {
        Set<String> documentosAnd = new HashSet<>();
        Set<String> documentosOr = new HashSet<>();

        if (indiceInvertido == null) {
            System.out.println("Error: El índice invertido no está cargado.");
            return new HashSet<>();
        }

        // procesar términos con AND (intersección)
        for (String termino : consultaAnalizada.get("AND")) {
            if (indiceInvertido.containsKey(termino)) {
                Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(termino).get("pesosDocumentos");
                if (documentosAnd.isEmpty()) {
                    documentosAnd.addAll(pesosDocumentos.keySet());
                } else {
                    documentosAnd.retainAll(pesosDocumentos.keySet());
                }
            } else {
                documentosAnd.clear();
                break;
            }
        }

        // procesar términos con OR (unión)
        for (String termino : consultaAnalizada.get("OR")) {
            if (indiceInvertido.containsKey(termino)) {
                Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(termino).get("pesosDocumentos");
                documentosOr.addAll(pesosDocumentos.keySet());
            }
        }

        // combinar resultados (prioridad a AND)
        Set<String> resultado = new HashSet<>(documentosAnd);
        resultado.addAll(documentosOr);
        return resultado;
    }

    // método para recuperar documentos para una frase
    private Set<String> recuperarDocumentosFrase(String[] terminos) {
        if (terminos.length == 0 || indiceInvertido == null) {
            return new HashSet<>();
        }

        Set<String> documentosCandidatos = new HashSet<>();
        if (indiceInvertido.containsKey(terminos[0])) {
            Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(terminos[0]).get("pesosDocumentos");
            documentosCandidatos.addAll(pesosDocumentos.keySet());
        }

        Set<String> resultado = new HashSet<>(documentosCandidatos);
        for (String documento : documentosCandidatos) {
            for (int i = 1; i < terminos.length; i++) {
                if (!indiceInvertido.containsKey(terminos[i]) ||
                        !((Map<String, Double>) indiceInvertido.get(terminos[i]).get("pesosDocumentos")).containsKey(documento)) {
                    resultado.remove(documento);
                    break;
                }
            }
        }
        return resultado;
    }

    // método para calcular el ranking de los documentos
    private List<ResultadoDocumento> calcularRanking(List<String> terminos, Set<String> documentosRelevantes) {
        List<ResultadoDocumento> resultados = new ArrayList<>();

        if (indiceInvertido == null || documentosRelevantes == null || longitudesDocumentos == null) {
            System.out.println("Error: Datos no cargados correctamente.");
            return resultados;
        }

        for (String documento : documentosRelevantes) {
            double puntuacion = 0.0;
            for (String termino : terminos) {
                if (indiceInvertido.containsKey(termino)) {
                    Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(termino).get("pesosDocumentos");
                    if (pesosDocumentos.containsKey(documento)) {
                        puntuacion += pesosDocumentos.get(documento);
                    }
                }
            }
            if (longitudesDocumentos.containsKey(documento)) {
                puntuacion = puntuacion / longitudesDocumentos.get(documento);
            }
            resultados.add(new ResultadoDocumento(documento, puntuacion));
        }

        resultados.sort((a, b) -> Double.compare(b.puntuacion, a.puntuacion));
        return resultados;
    }

    // método para mostrar resultados con formato y snippets
    private void mostrarResultados(List<ResultadoDocumento> resultados, String consulta) {
        if (resultados.isEmpty()) {
            System.out.println("No se encontraron documentos relevantes.");
            return;
        }

        System.out.println("\n=== RESULTADOS PARA: \"" + consulta + "\" ===");
        for (int i = 0; i < Math.min(resultados.size(), 5); i++) {
            ResultadoDocumento documento = resultados.get(i);
            System.out.printf(
                    "%d. %s (Puntuación: %.4f)%n",
                    i + 1,
                    documento.nombreDocumento,
                    documento.puntuacion
            );

            try {
                String contenido = cargarContenidoDocumento(documento.nombreDocumento);
                String fragmento = obtenerFragmento(contenido, consulta);
                System.out.println("   Fragmento: " + resaltarTermino(fragmento, consulta));
            } catch (IOException e) {
                System.out.println("   Fragmento: No disponible");
            }
        }
    }

    // método para cargar el contenido de un documento
    private String cargarContenidoDocumento(String rutaDocumento) throws IOException {
        return new String(Files.readAllBytes(Paths.get("corpus/" + rutaDocumento)));
    }

    // método para obtener un fragmento del documento que contenga el término de búsqueda
    private String obtenerFragmento(String contenido, String consulta) {
        String[] terminos = consulta.toLowerCase().split("\\s+");
        for (String termino : terminos) {
            int indice = contenido.toLowerCase().indexOf(termino);
            if (indice != -1) {
                int inicio = Math.max(0, indice - 30);
                int fin = Math.min(contenido.length(), indice + termino.length() + 30);
                String fragmento = contenido.substring(inicio, fin).trim();
                if (inicio > 0) fragmento = "..." + fragmento;
                return fragmento;
            }
        }
        return "No se encontró el término en el documento.";
    }

    // método para resaltar el término en el fragmento
    private String resaltarTermino(String fragmento, String termino) {
        return fragmento.replaceAll(
                "(?i)(" + Pattern.quote(termino) + ")",
                "\u001B[31m$1\u001B[0m"
        );
    }
}
