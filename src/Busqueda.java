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
    }

}