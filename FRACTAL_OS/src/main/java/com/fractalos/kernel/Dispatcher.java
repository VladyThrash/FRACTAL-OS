package com.fractalos.kernel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.fractalos.ipc.SystemMessage;
import com.fractalos.ipc.IPCBus;

//Para el despachador se utiliza el paquete java.util.concurrent.
//Un ExecutorService nos permite simular exactamente un procesador con (n) cantidad de núcleos.
//El Despachador simplemente toma un Proceso de la cola del Planificador y lo envía al pool.
//Si hay un "núcleo" (hilo) libre, el proceso se ejecuta; si no, espera.

public class Dispatcher {

    //La instancia del executor service nos permite hacer una representación del HAL (Abstraccón lógica del hardware).
    //Se simula un procesador de 4 núcleos.
    private static final ExecutorService cpuPool = Executors.newFixedThreadPool(4);

    //Mapa concurrente para mantener el control de los procesos en ejecución.
    //Relaciona el Process ID del con su objeto Future.
    private static final Map<Integer, Future<?>> activeProcesses = new ConcurrentHashMap<>();

    //Método estático: Prepara el entorno antes de que el proceso entre a la CPU.
    public static void loadContext(Process p) {
        p.setActualState(Process.STATE.RUNNING_PROCESS);
        //Definir el tiempo de la primera ejecución
        long actualTime = System.currentTimeMillis();
        if (p.getFirstEjecutionTime() == -1) {
            p.setFirstEjecutionTime(actualTime);
        }
        //Acumular el tiempo de espera
        long waitingTime = actualTime - p.getLastReadyTime();
        p.setWaitAccumulatedTime(p.getWaitAccumulatedTime() + waitingTime);
    }

    //Método estático: Toma un proceso seleccionado por el Scheduler y lo lanza a los núcleos.
    public static void dispatch(Process p) {
        Dispatcher.loadContext(p);
        //Enviamos el proceso al pool de hilos y guardamos su "controlador" (Future)
        Future<?> controlHilo = cpuPool.submit(p);
        activeProcesses.put(p.getPid(), controlHilo);
    }

    //Método estático: Guarda el estado cuando un proceso sale de la CPU antes de terminar.
    public static void saveContext(Process p) {
        p.setActualState(Process.STATE.STANDBY_PROCESS);
        p.setLastReadyTime(System.currentTimeMillis());
    }

    //Método estático: Fuerza la detención de un proceso en ejecución (Mecanismo de Expropiación).
    public static boolean interruptEjecution(Process p) {
        Future<?> controlHilo = Dispatcher.activeProcesses.get(p.getPid());

        if (controlHilo != null && !controlHilo.isDone()) {
            //El parámetro 'true' lanza una InterruptedException dentro del run() del proceso.
            controlHilo.cancel(true);
            Dispatcher.activeProcesses.remove(p.getPid());

            Dispatcher.saveContext(p);
            return true;
        }
        return false;
    }

    //Método estático: Ejecuta la limpieza cuando un proceso termina su código de manera natural.
    public static void endProcess(Process p) {
        p.setActualState(Process.STATE.FINISHED_PROCESS);

        //Notificar mediante IPC liberación de memoria.
        SystemMessage msg1 = new SystemMessage(
                SystemMessage.Topic.MEMORY_FREE_REQUEST,
                0,          //sourcePid: 0 indica que el remitente es el Kernel.
                p.getPid(),          //targetPid: El proceso que acaba de morir.
                null
        );
        SystemMessage msg2 = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE,
                0,          //sourcePid: 0 indica que el remitente es el Kernel.
                p.getPid(),          //targetPid: El proceso que acaba de morir.
                "Kernel: Proceso PID [" + p.getPid() + "] finalizó su ráfaga."
        );

        IPCBus.sendMessageToMemory(msg1);
        IPCBus.sendMessageToShell(msg2);
        Dispatcher.activeProcesses.remove(p.getPid());
        KernelDaemon.GPT.remove(p.getPid()); //Removemos de la Tabla Global de Procesos.
        KernelDaemon.evaluateExpropriation(); //Evaluamos la prioridad de los procesos.
    }

    //Método estático: Mata un proceso forzosamente si se encuentra en ejecución.
    public static boolean killProcess(int pid) {
        Future<?> threadControl = activeProcesses.get(pid);

        if (threadControl != null && !threadControl.isDone()) {
            //Cancel(true) lanza la InterruptedException dentro del while() del proceso.
            threadControl.cancel(true);
            activeProcesses.remove(pid);

            //Notificar mediante IPC liberación de la memoria.
            SystemMessage msg = new SystemMessage(
                    SystemMessage.Topic.MEMORY_FREE_REQUEST,
                    0,          //sourcePid: 0 indica que el remitente es el Kernel.
                    pid,          //targetPid: El proceso que acaba de morir.
                    null
            );
            IPCBus.sendMessageToMemory(msg);

            return true; //El proceso fue aniquilado con éxito en la CPU.
        }
        return false; //El proceso no estaba en la CPU.
    }

    //Método estático: Retorna los PIDs de los procesos que están ocupando un núcleo.
    public static java.util.Set<Integer> getActivePids() {
        return activeProcesses.keySet();
    }

   //Método estático: Detiene el Executor Service y limpia el mapa de procesos activos.
    public static void shutDown() {
        cpuPool.shutdownNow();
        Dispatcher.activeProcesses.clear();
        Scheduler.clearAllProcesses();
        IPCBus.clearComunicationBus();
    }
}
