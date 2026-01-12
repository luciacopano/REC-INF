import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        try {
            System.out.println("=== INICIANDO SISTEMA DE BÚSQUEDA ===\n");
            
            // Paso 1: Ejecutar Indexación
            System.out.println("--- PASO 1: INDEXACIÓN DE DOCUMENTOS ---");
            Indexacion.main(new String[]{});
            
            System.out.println("\n--- PASO 2: MOTOR DE BÚSQUEDA ---\n");
            // Paso 2: Ejecutar Búsqueda
            Busqueda.main(new String[]{});
            
        } catch (Exception e) {
            System.err.println("Error en la ejecución: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
