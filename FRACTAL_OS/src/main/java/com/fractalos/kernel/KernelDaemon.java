package com.fractalos.kernel;
import com.fractalos.ipc.IPCBus;
import com.fractalos.ipc.SystemMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//El kernel necesita un hilo en segundo plano sin intervención directa (Daemon en terminología UNIX).
//Para lograr este cometido, esta clase implementa Runnable para ejecutarse como un hilo secundario.
//El Daemon recibe mensajes del IPC mediante IPCBus y dadas las peticiones recibidas, ejecuta los métodos
//definidos en el módulo del kernel.

public class KernelDaemon implements Runnable {
    public static final Map<Integer, Process> GPT = new ConcurrentHashMap<>(); //Tabla Global de Procesos.
    private int pidCounter = 1; //Generador de Process ID's

    @Override
    public void run() {
        System.out.println("Kernel Daemon iniciado. Escuchando peticiones IPC...");
        try {
            while (true) {
                //Toma los mensajes recibidos por IPC y procesa las peticiones.
                SystemMessage msg = IPCBus.kernelMailbox.take();
                processMessage(msg);
            }
        } catch (InterruptedException e) {
            System.out.println("Kernel Daemon interrumpido de forma crítica.");
            Dispatcher.shutDown();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error interno en el Kernel procesando mensaje: " + e.getMessage());
        }
    }

    //Peticiones recibidas.
    private void processMessage(SystemMessage msg) {
        if (msg.getTopic() == SystemMessage.Topic.PROCESS_CREATE_REQUEST) {
            createProcess(msg);
        } else if (msg.getTopic() == SystemMessage.Topic.SYSTEM_SHUTDOWN_REQUEST) {
            systemShutDown(msg);
        } else if (msg.getTopic() == SystemMessage.Topic.PROCESS_LIST_REQUEST) {
            listActiveProcesses(msg);
        } else if (msg.getTopic() == SystemMessage.Topic.PROCESS_KILL_REQUEST) {
            killProcess(msg);
        }
        //...
    }

    //Petición: Crear y encolar un nuevo proceso.
    private void createProcess(SystemMessage msg) {
        String[] tokens = (String[]) msg.getPayload();
        if (tokens.length < 3) throw new IllegalArgumentException("Faltan argumentos.");

        int priority = Integer.parseInt(tokens[1]);
        int remProcessing = Integer.parseInt(tokens[2]);

        Process newProcess = new Process(pidCounter++, priority, remProcessing);
        GPT.put(newProcess.getPid(), newProcess); //Registramos el proceso en la tabla global.
        Scheduler.addProcess(newProcess);

        SystemMessage answer = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                "Kernel: Proceso PID [" + newProcess.getPid() + "] creado y encolado."
        );
        IPCBus.sendMessageToShell(answer);

        Process nextProcess = Scheduler.getNextProcess();
        if (nextProcess != null) {
            Dispatcher.dispatch(nextProcess);
        }
    }

    //Petición: Limpiar estructuras y apagar el sistema mediante secuencia segura.
    private void systemShutDown(SystemMessage msg) {
        System.out.println("Secuencia de apagado iniciada por el usuario.");
        SystemMessage answer = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                "Kernel: Sistema detenido. Puede cerrar la ventana de forma segura."
        );
        IPCBus.sendMessageToShell(answer);
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        Dispatcher.shutDown();
        System.exit(0);
    }

    //Petición: Listar todos los procesos activos y regresar la cadena para imprimir en Shell.
    private void listActiveProcesses(SystemMessage msg) {
        StringBuilder tabla = new StringBuilder();
        tabla.append("\n--- TABLA DE PROCESOS (FRACTAL-OS) ---\n");
        tabla.append(String.format("%-5s | %-10s | %-15s | %-10s\n", "PID", "PRIORIDAD", "ESTADO ACTUAL", "RÁFAGAS"));
        tabla.append("------------------------------------------------------\n");

        if (GPT.isEmpty()) {
            tabla.append("No hay procesos activos en el sistema.\n");
        } else {
            //Recorremos todos los PCBs registrados.
            for (Process p : GPT.values()) {
                tabla.append(String.format("%-5d | %-10d | %-15s | %-10d\n",
                        p.getPid(),
                        p.getPriority(),
                        // Asumiendo que agregaste un getActualState() a Process.java
                        p.getActualState().toString(),
                        p.getRemProcessing()
                ));
            }
        }
        SystemMessage answer = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, tabla.toString()
        );
        IPCBus.sendMessageToShell(answer);
    }

    //Petición: Matar o eliminar un proceso activo o en ejecución.
    private void killProcess(SystemMessage msg){
        String[] tokens = (String[]) msg.getPayload();
        int targetPid;

        //Blindaje contra caracteres que no sean números.
        try {
            targetPid = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            IPCBus.sendMessageToShell(new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, "Kernel Error: El PID debe ser un número entero."
            ));
            return;
        }

        //Verificamos si el proceso existe en la Tabla Global.
        Process p = GPT.get(targetPid);
        if (p == null) {
            IPCBus.sendMessageToShell(new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, "Kernel Error: No se encontró ningún proceso con PID [" + targetPid + "]."
            ));
            return;
        }

        //Intentar sacarlo del Scheduler o del Dispatcher.
        boolean estabaEnCola = Scheduler.deleteProcess(targetPid);
        boolean estabaEnCPU = false;
        if (!estabaEnCola) {
            estabaEnCPU = Dispatcher.killProcess(targetPid);
        }

        //Limpieza absoluta del registro.
        p.setActualState(Process.STATE.FINISHED_PROCESS);
        GPT.remove(targetPid);

        //Confirmar al usuario.
        String estado = estabaEnCola ? "eliminado de la cola." : "aniquilado en la CPU.";
        IPCBus.sendMessageToShell(new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, "Kernel: Proceso PID [" + targetPid + "] " + estado
        ));

        //Debemos despachar al siguiente proceso en la cola.
        if (estabaEnCPU) {
            Process procesoSiguiente = Scheduler.getNextProcess();
            if (procesoSiguiente != null) {
                Dispatcher.dispatch(procesoSiguiente);
            }
        }
    }

}
