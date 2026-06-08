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
        } else if (msg.getTopic() == SystemMessage.Topic.PROCESS_PRIORITY_CHANGE_REQUEST) {
            changePriority(msg);
        }
        //...
    }

    //Petición: Crear y encolar un nuevo proceso.
    private void createProcess(SystemMessage msg) {
        String[] tokens = (String[]) msg.getPayload();
        if (tokens.length < 4){
            SystemMessage answer = new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                    "Faltan argumentos: test_proc [prioridad] [refagas] [memoria]."
            );
            IPCBus.sendMessageToShell(answer);
            return;
        }

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

        //Solicitamos espacio en RAM.
        int requestMemory = Integer.parseInt(tokens[3]);
        int[] dataMemory = {newProcess.getPid(), requestMemory};
        SystemMessage peticionMemoria = new SystemMessage(
                SystemMessage.Topic.MEMORY_ALLOCATION_REQUEST,
                0, 0, dataMemory
        );
        IPCBus.sendMessageToMemory(peticionMemoria);

        evaluateExpropriation(); //Evaluamos la prioridad de los procesos.
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

    //Petición: Cambiar la prioridad de un proceso activo.
    private void changePriority(SystemMessage msg){
        String[] tokens = (String[]) msg.getPayload();
        int targetPid;
        int newPriority;

        //Blindaje contra caracteres que no sean números.
        try {
            targetPid = Integer.parseInt(tokens[1]);
            newPriority = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException e) {
            IPCBus.sendMessageToShell(new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, "Kernel Error: El PID y la Prioridad deben ser números enteros."
            ));
            return;
        }

        //Buscar en la Tabla Global de Procesos
        Process p = GPT.get(targetPid);
        if (p == null) {
            IPCBus.sendMessageToShell(new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, "Kernel Error: No se encontró proceso con PID [" + targetPid + "]."
            ));
            return;
        }

        //Extraer de la cola.
        boolean estabaEnCola = Scheduler.expelProcess(targetPid);

        //Aplicar el cambio de prioridad al PCB.
        p.setPriority(newPriority);

        //Lógica de re-inserción o notificación.
        if (estabaEnCola) {
            Scheduler.addProcess(p);
            IPCBus.sendMessageToShell(new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                    "Kernel: Prioridad del PID [" + targetPid + "] actualizada a " + newPriority + ". Reordenado en la cola."
            ));
        } else {
            IPCBus.sendMessageToShell(new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                    "Kernel: Prioridad del PID [" + targetPid + "] actualizada a " + newPriority + " (Actualmente en ejecución)."
            ));
        }
        evaluateExpropriation(); //Evaluamos la prioridad de los procesos.
    }

    //Método de expropiación: Evalúa la prioridad de los procesos que están en Dispatcher, busca los procesos menos
    //prioritarios y los saca para atender a los más prioritarios.
    public static void evaluateExpropriation() {
        //Núcleos libres.
        if (Dispatcher.getActivePids().size() < 4) {
            Process next = Scheduler.getNextProcess();
            if (next != null) {
                Dispatcher.dispatch(next);
            }
            return; //Termina la evaluación.
        }

        //Si la CPU está llena, seleccionamos el más prioritario.
        Process best = Scheduler.peekNextProcess();
        //System.out.println("Mejor prioridad: "+ best.getPriority());
        if (best == null) return; //Cola vacía.

        //Busca el proceso en Dispatcher con menor prioridad.
        Process weak = null;
        for (Integer pid : Dispatcher.getActivePids()) {
            Process pActive = KernelDaemon.GPT.get(pid);
            if (pActive != null) {
                if (weak == null || pActive.getPriority() < weak.getPriority()) {
                    weak = pActive;
                }
            }
        }

        //Se decide quien entra al Dispatcher.
        if (weak != null && best.getPriority() > weak.getPriority()) {
            boolean expropriated = Dispatcher.interruptEjecution(weak);
            if (expropriated) {
                //Regresamos al débil a la fila de espera del Scheduler.
                weak.setActualState(Process.STATE.STANDBY_PROCESS);
                Scheduler.addProcess(weak);
                //Sacamos al candidato VIP de la fila y lo mandamos al núcleo que se acaba de liberar.
                Process vip = Scheduler.getNextProcess();
                Dispatcher.dispatch(vip);

                //IPCBus.sendMessageToShell(new SystemMessage(
                //        SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                //        "Kernel: [Preemption] PID " + weak.getPid() + " expropiado de la CPU. Entra PID " + vip.getPid()
                //));
            }
        }
    }

}
