package com.fractalos.kernel;
import com.fractalos.ipc.IPCBus;
import com.fractalos.ipc.SystemMessage;

//El kernel necesita un hilo en segundo plano sin intervención directa (Daemon en terminología UNIX).
//Para lograr este cometido, esta clase implementa Runnable para ejecutarse como un hilo secundario.
//El Daemon recibe mensajes del IPC mediante IPCBus y dadas las peticiones recibidas, ejecuta los métodos
//definidos en el módulo del kernel.

public class KernelDaemon implements Runnable {
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
}
