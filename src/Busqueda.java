import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

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
        busqueda.cargarIndiceDesdeTxt("output/index.txt", "output/lengths.txt");
        busqueda.motor();
    }

    // método para cargar el índice desde archivos .txt
    public void cargarIndiceDesdeTxt(String rutaIndiceTxt, String rutaLongitudesTxt) throws IOException {
        cargarIndice(rutaIndiceTxt, rutaLongitudesTxt);
    }

    // método para cargar el índice y las longitudes desde archivos TXT
    public void cargarIndice(String rutaIndice, String rutaLongitudes) {
        try {
            // cargar índice invertido desde TXT
            indiceInvertido = new HashMap<>();
            List<String> lineasIndice = Files.readAllLines(Paths.get(rutaIndice));
            
            for (String linea : lineasIndice) {
                String[] partes = linea.split("\\|");
                if (partes.length < 2) continue;
                
                String termino = partes[0].trim();
                double idf = Double.parseDouble(partes[1].trim().replace(",", "."));
                
                Map<String, Object> datosTermino = new HashMap<>();
                datosTermino.put("idf", idf);
                
                Map<String, Double> pesosDocumentos = new HashMap<>();
                if (partes.length > 2) {
                    String pesosDocumentosStr = partes[2].trim();
                    // Parsear formato: (doc1 peso1) (doc2 peso2) ...
                    String[] entradasDocumentos = pesosDocumentosStr.split("\\)\\s*\\(|[()]");
                    for (String entrada : entradasDocumentos) {
                        if (entrada.trim().isEmpty()) continue;
                        String[] pesoDocumento = entrada.trim().split("\\s+");
                        if (pesoDocumento.length == 2) {
                            String idDocumento = pesoDocumento[0];
                            double peso = Double.parseDouble(pesoDocumento[1].replace(",", "."));
                            pesosDocumentos.put(idDocumento, peso);
                        }
                    }
                }
                datosTermino.put("pesosDocumentos", pesosDocumentos);
                indiceInvertido.put(termino, datosTermino);
            }
            
            // cargar longitudes de documentos desde TXT
            longitudesDocumentos = new HashMap<>();
            List<String> lineasLongitudes = Files.readAllLines(Paths.get(rutaLongitudes));
            
            for (String linea : lineasLongitudes) {
                String[] partes = linea.trim().split("\\s+");
                if (partes.length == 2) {
                    String idDocumento = partes[0];
                    double longitud = Double.parseDouble(partes[1].replace(",", "."));
                    longitudesDocumentos.put(idDocumento, longitud);
                }
            }
            
            System.out.println("Índice cargado exitosamente.");
        } catch (Exception e) {
            System.err.println("Error al cargar el índice: " + e.getMessage());
        }
    }

    // comienza el motor de búsqueda interactivo
    public void motor() {
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
            System.out.println("El término '" + consulta + "' no es un término de búsqueda válido.");
            return;
        }

        System.out.print("Introduce el número de documentos que deseas ver: ");
        int numDocumentos;
        try {
            numDocumentos = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Número no válido. Mostrando 5 documentos por defecto.");
            numDocumentos = 5;
        }

        // calcular el coseno de cada documento
        Map<String, Double> cosenos = calcularCosenosUnTermino(terminoP);

        // ordenar los documentos
        Vector<String> documentosOrdenados = ordenarDocumentos(cosenos);

        // mostrar los resultados
        mostrarResultadosCosenos(documentosOrdenados, cosenos, consulta, numDocumentos);
    }

    // método para la búsqueda con operadores
    private void busquedaOperadores() {
        System.out.print("Introduce el tipo de consulta que desea realizar (AND/OR): ");
        String tipoConsulta = scanner.nextLine().trim().toUpperCase();
        while (!tipoConsulta.equals("AND") && !tipoConsulta.equals("OR")) {
            System.out.print("Tipo de consulta no válido. Introduce AND o OR: ");
            tipoConsulta = scanner.nextLine().trim().toUpperCase();
        }

        System.out.print("Introduce los términos de la consulta separados por espacios: ");
        String consulta = scanner.nextLine().trim();
        String[] terminos = consulta.split(" ");

        System.out.print("Introduce el número de documentos que deseas ver: ");
        int numDocumentos;
        try {
            numDocumentos = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Número no válido. Mostrando 5 documentos por defecto.");
            numDocumentos = 5;
        }

        // Calcular el coseno de cada documento
        Map<String, Double> cosenosConsulta = calcularCosenosMultiplesTerminos(terminos, tipoConsulta);

        if (cosenosConsulta.isEmpty()) {
            System.out.println("No se encontraron documentos relevantes.");
            return;
        }

        // Ordenar los documentos
        Vector<String> documentosOrdenados = ordenarDocumentos(cosenosConsulta);

        // Mostrar los resultados
        mostrarResultadosCosenos(documentosOrdenados, cosenosConsulta, consulta, numDocumentos);
    }

    // método para la búsqueda de una frase
    private void busquedaFrase() {
        System.out.print("Introduce la frase a buscar: ");
        String consulta = scanner.nextLine().trim();

        List<String> terminosProcesados = procesarConsulta(consulta);
        if (terminosProcesados.isEmpty()) {
            System.out.println("La frase '" + consulta + "' no contiene términos de búsqueda válidos.");
            return;
        }

        System.out.print("Introduce el número de documentos que deseas ver: ");
        int numDocumentos;
        try {
            numDocumentos = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Número no válido. Mostrando 5 documentos por defecto.");
            numDocumentos = 5;
        }

        // Calcular el coseno de cada documento
        String[] terminosArray = terminosProcesados.toArray(new String[0]);
        Map<String, Double> cosenosConsulta = calcularCosenosFrase(terminosArray);

        if (cosenosConsulta.isEmpty()) {
            System.out.println("No se encontraron documentos relevantes.");
            return;
        }

        // Ordenar los documentos
        Vector<String> documentosOrdenados = ordenarDocumentos(cosenosConsulta);

        // Mostrar los resultados
        mostrarResultadosCosenos(documentosOrdenados, cosenosConsulta, consulta, numDocumentos);
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

        // Guardamos la versión legible para mostrarla completa al usuario
        String terminoVisible = termino;

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

        // Mostramos la forma completa, no la stemmizada, para que no aparezca cortada
        System.out.println("Consulta procesada: " + terminoVisible);
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
        List<String> terminosVisibles = new ArrayList<>();

        try {
            Set<String> stopwords = cargarStopwords("stopwords.txt");
            for (String termino : terminos) {
                if (termino.length() < 3) continue;
                String terminoVisible = termino;
                String raiz = aplicarStemming(termino);
                if (!stopwords.contains(raiz)) {
                    terminosVisibles.add(terminoVisible);
                    terminosProcesados.add(raiz);
                }
            }
        } catch (IOException e) {
            System.err.println("Error al cargar stopwords: " + e.getMessage());
        }

        System.out.print("Consulta procesada: ");
        for (String termino : terminosVisibles) {
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

    // método para calcular el coseno de un término
    private Map<String, Double> calcularCosenosUnTermino(String termino) {
        Map<String, Double> cosenos = new HashMap<>();

        if (!indiceInvertido.containsKey(termino)) {
            return cosenos;
        }

        double idf = (double) indiceInvertido.get(termino).get("idf");
        Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(termino).get("pesosDocumentos");

        for (String documento : pesosDocumentos.keySet()) {
            double peso = pesosDocumentos.get(documento);
            double coseno = peso / longitudesDocumentos.get(documento); // Simplificado, ya que idf se cancela
            cosenos.put(documento, coseno);
        }

        return cosenos;
    }

    // método para calcular el coseno de múltiples términos
    private Map<String, Double> calcularCosenosMultiplesTerminos(String[] terminos, String tipoConsulta) {
        Map<String, Double> cosenosConsulta = new HashMap<>();
        boolean noContieneTodos = false;

        // Calcular la consulta normalizada
        double consultaNormalizada = 0;
        for (String terminoConsulta : terminos) {
            terminoConsulta = procesarTermino(terminoConsulta);
            if (indiceInvertido.containsKey(terminoConsulta)) {
                double idf = (double) indiceInvertido.get(terminoConsulta).get("idf");
                consultaNormalizada += Math.pow(idf, 2);
            } else {
                noContieneTodos = true;
            }
        }
        consultaNormalizada = Math.sqrt(consultaNormalizada);

        // Calcular el coseno de cada documento
        for (String terminoConsulta : terminos) {
            terminoConsulta = procesarTermino(terminoConsulta);
            if (indiceInvertido.containsKey(terminoConsulta)) {
                double idf = (double) indiceInvertido.get(terminoConsulta).get("idf");
                Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(terminoConsulta).get("pesosDocumentos");
                for (String documento : pesosDocumentos.keySet()) {
                    double peso = pesosDocumentos.get(documento);
                    if (!cosenosConsulta.containsKey(documento)) {
                        cosenosConsulta.put(documento, 0.0);
                    }
                    cosenosConsulta.put(documento, cosenosConsulta.get(documento) + peso * idf);
                }
            }
        }

        // Normalizar los cosenos
        for (String documento : cosenosConsulta.keySet()) {
            double valor = cosenosConsulta.get(documento) / (longitudesDocumentos.get(documento) * consultaNormalizada);
            cosenosConsulta.put(documento, valor);
        }

        // Si la consulta es AND y no contiene todos los términos, mostrar mensaje
        if (tipoConsulta.equals("AND") && noContieneTodos) {
            System.out.println("No se encontraron todos los términos en los documentos.");
            return new HashMap<>();
        }

        // Si es una consulta AND, eliminar los documentos que no contengan todos los términos
        if (tipoConsulta.equals("AND")) {
            List<String> toRemove = new ArrayList<>();
            for (String documento : cosenosConsulta.keySet()) {
                for (String terminoConsulta : terminos) {
                    terminoConsulta = procesarTermino(terminoConsulta);
                    if (!indiceInvertido.containsKey(terminoConsulta) ||
                            !((Map<String, Double>) indiceInvertido.get(terminoConsulta).get("pesosDocumentos")).containsKey(documento)) {
                        toRemove.add(documento);
                        break;
                    }
                }
            }
            for (String documento : toRemove) {
                cosenosConsulta.remove(documento);
            }
        }

        return cosenosConsulta;
    }

    // método para calcular el coseno de una frase
    private Map<String, Double> calcularCosenosFrase(String[] terminos) {
        // Primero recuperamos los documentos que contienen la frase
        Set<String> documentosRelevantes = recuperarDocumentosFrase(terminos);

        // Calculamos el coseno para estos documentos
        Map<String, Double> cosenosConsulta = new HashMap<>();

        // calcular la consulta normalizada
        double consultaNormalizada = 0;
        for (String terminoConsulta : terminos) {
            terminoConsulta = procesarTermino(terminoConsulta);
            if (indiceInvertido.containsKey(terminoConsulta)) {
                double idf = (double) indiceInvertido.get(terminoConsulta).get("idf");
                consultaNormalizada += Math.pow(idf, 2);
            }
        }
        consultaNormalizada = Math.sqrt(consultaNormalizada);

        // calcular el coseno de cada documento
        for (String terminoConsulta : terminos) {
            terminoConsulta = procesarTermino(terminoConsulta);
            if (indiceInvertido.containsKey(terminoConsulta)) {
                double idf = (double) indiceInvertido.get(terminoConsulta).get("idf");
                Map<String, Double> pesosDocumentos = (Map<String, Double>) indiceInvertido.get(terminoConsulta).get("pesosDocumentos");
                for (String documento : documentosRelevantes) {
                    if (pesosDocumentos.containsKey(documento)) {
                        double peso = pesosDocumentos.get(documento);
                        if (!cosenosConsulta.containsKey(documento)) {
                            cosenosConsulta.put(documento, 0.0);
                        }
                        cosenosConsulta.put(documento, cosenosConsulta.get(documento) + peso * idf);
                    }
                }
            }
        }

        // normalizar los cosenos
        for (String documento : cosenosConsulta.keySet()) {
            double valor = cosenosConsulta.get(documento) / (longitudesDocumentos.get(documento) * consultaNormalizada);
            cosenosConsulta.put(documento, valor);
        }

        return cosenosConsulta;
    }

    // método para ordenar documentos según su puntuación
    private Vector<String> ordenarDocumentos(Map<String, Double> cosenos) {
        Vector<String> documentosOrdenados = new Vector<>();
        for (String documento : cosenos.keySet()) {
            int i = 0;
            while (i < documentosOrdenados.size() && cosenos.get(documentosOrdenados.get(i)) > cosenos.get(documento)) {
                i++;
            }
            documentosOrdenados.add(i, documento);
        }
        return documentosOrdenados;
    }

    // método para obtener un fragmento del documento que contenga el término de búsqueda
    private String obtenerFragmento(String contenido, String consulta) {
        String[] terminos = consulta.toLowerCase().split("\\s+");
        String[] palabras = contenido.split("\\s+"); // Dividir el contenido en palabras

        for (String termino : terminos) {
            // Buscar el término en el contenido
            for (int i = 0; i < palabras.length; i++) {
                if (palabras[i].toLowerCase().contains(termino)) {
                    // calcular el rango de palabras a mostrar (5 antes y 5 después)
                    int inicio = Math.max(0, i - 5);
                    int fin = Math.min(palabras.length, i + 6); // +6 para incluir 5 palabras después

                    // construir el fragmento
                    StringBuilder fragmentoBuilder = new StringBuilder();
                    for (int j = inicio; j < fin; j++) {
                        if (j > inicio) {
                            fragmentoBuilder.append(" ");
                        }
                        fragmentoBuilder.append(palabras[j]);
                    }
                    String fragmento = fragmentoBuilder.toString();

                    // Añadir "..." si el fragmento no empieza al inicio del documento
                    if (inicio > 0) fragmento = "..." + fragmento;
                    // Añadir "..." si el fragmento no termina al final del documento
                    if (fin < palabras.length) fragmento = fragmento + "...";

                    return fragmento;
                }
            }
        }
        return "No se encontró el término en el documento.";
    }

    // método para cargar el contenido de un documento
    private String cargarContenidoDocumento(String rutaDocumento) throws IOException {
        return new String(Files.readAllBytes(Paths.get("corpus/" + rutaDocumento)));
    }

    // método para resaltar el término en el fragmento de color lila
    private String resaltarTermino(String fragmento, String consulta) {
        String[] terminos = consulta.toLowerCase().split("\\s+");
        for (String termino : terminos) {
            // resaltar cada término de la consulta en el fragmento en color lila :)
            fragmento = fragmento.replaceAll(
                    "(?i)(" + Pattern.quote(termino) + ")",
                    "\u001B[35m$1\u001B[0m"
            );
        }
        return fragmento;
    }

    // método para mostrar resultados con cosenos
    private void mostrarResultadosCosenos(Vector<String> documentosOrdenados, Map<String, Double> cosenos, String consulta, int numDocumentos) {
        System.out.println("\n=== RESULTADOS DE LA BÚSQUEDA ===");
        System.out.println("Consulta: " + consulta);
        System.out.println("Documentos encontrados: " + documentosOrdenados.size());
        
        int contador = 0;
        for (String documento : documentosOrdenados) {
            if (contador >= numDocumentos) break;
            
            double coseno = cosenos.get(documento);
            System.out.println("\n" + (contador + 1) + ". " + documento + " (puntuación: " + String.format("%.4f", coseno) + ")");
            
            try {
                String contenido = cargarContenidoDocumento(documento);
                String fragmento = obtenerFragmento(contenido, consulta);
                String fragmentoResaltado = resaltarTermino(fragmento, consulta);
                System.out.println("   Fragmento: " + fragmentoResaltado);
            } catch (IOException e) {
                System.out.println("   Error al cargar el documento.");
            }
            
            contador++;
        }
    }
}

