package com.fractalos.modules.memory;

import com.fractalos.ipc.IPCModule;
import com.fractalos.ipc.IPCBus;
import com.fractalos.ipc.SystemMessage;

//Al igual que KernelDaemon y Shell, MemoryManager es un hilo (Daemon) independiente, por ello implementa Runnable.
//Contrata IPCModule para recibir y enviar peticiones a otros módulos del sistema. Se simula la memoria RAM utilizando
//un mapa de bits (arreglo de enteros) donde cada índice se pinta con el proceso que la está ocupando (Paginación).

public class MemoryManager implements IPCModule, Runnable {

    //Atributos de la clase
    private boolean active = true;
    private static final int TOTAL_RAM_KB = 1024; //Espacio total de la memoria.
    private static final int PAGE_SIZE_KB = 64; //Tamaño de página.
    private static final int TOTAL_FRAMES = TOTAL_RAM_KB / PAGE_SIZE_KB; //Número total de segmentos de memoria.
    private final int[] memoryFrames = new int[TOTAL_FRAMES]; //Mapa de bits, donde cada índice es un marco de página.

    //Métodos de la interfaz IPCModule.
    @Override
    public String getModuleName() {
        return "FRACTAL_MEMORY_MANAGER";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void shutDownModule() {
        this.active = false;
        System.out.println("Módulo de Memoria apagado.");
    }

    @Override
    public void processMessage(SystemMessage msg) {
        switch (msg.getTopic()) {
            case MEMORY_MAP_REQUEST:
                //Pedimos un mapeo de RAM, muestra los marcos ocupados por procesos.
                sendMapToShell();
                break;
            case MEMORY_FREE_REQUEST:
                //El Kernel avisa que un PID murió, liberamos su RAM.
                free(msg.getTargetPid());
                break;
            case MEMORY_ALLOCATION_REQUEST:
                //Pedimos asignación de memoria para un proceso.
                malloc(msg);
                break;
            default:
                break;
        }
    }

    //Implementamos el contrato de Runnable.
    @Override
    public void run() {
        this.active = true;
        System.out.println("Gestor de Memoria iniciado. Administrando " + TOTAL_RAM_KB + " KB.");
        try {
            while (active) {
                SystemMessage msg = IPCBus.memoryMailbox.take();
                processMessage(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutDownModule();
        }
    }

    //Petición: Liberar los marcos de memoria utilizados por un proceso activo.
    private void free(int pid) {
        int freePages = 0;
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            if (memoryFrames[i] == pid) {
                memoryFrames[i] = 0; // 0 significa libre
                freePages++;
            }
        }

        //Avisar a la Shell que se liberó espacio en memoria.
        if (freePages > 0) {
            SystemMessage aviso = new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                    "Memoria: Liberados " + (freePages * PAGE_SIZE_KB) + " KB del PID " + pid
            );
            IPCBus.sendMessageToShell(aviso);
        }
    }

    //Petición: Mapear memoria RAM.
    private void sendMapToShell() {
        StringBuilder map = new StringBuilder();
        map.append("\n--- MAPA DE MEMORIA RAM (").append(TOTAL_RAM_KB).append(" KB) ---\n");

        int lib = 0;
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            if (memoryFrames[i] == 0) {
                map.append("[ 00 ] ");
                lib++;
            } else {
                map.append(String.format("[ P%d ] ", memoryFrames[i]));
            }

            // Salto de línea cada 4 marcos para que se vea como una cuadrícula
            if ((i + 1) % 4 == 0) map.append("\n");
        }
        map.append("-------------------------------------\n");
        map.append("Páginas Libres: ").append(lib).append("/").append(TOTAL_FRAMES).append("\n");

        SystemMessage respuesta = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, map.toString()
        );
        IPCBus.sendMessageToShell(respuesta);
    }

    //Petición: Asignar a un proceso espacio en memoria.
    private void malloc(SystemMessage msg) {
        int[] request = (int[]) msg.getPayload();
        int pid = request[0];
        int requestKB = request[1];

        //Calcular cuántos marcos (páginas) se necesitan.
        //Math.ceil para redondear hacia arriba. Si pide 65KB, necesita 2 marcos de 64KB.
        int needFrames = (int) Math.ceil((double) requestKB / PAGE_SIZE_KB);

        //Contar la memoria total disponible actualmente.
        int freeFrames = 0;
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            if (memoryFrames[i] == 0) {
                freeFrames++;
            }
        }

        //No hay suficiente espacio disponible.
        if (freeFrames < needFrames) {
            SystemMessage error = new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                    "Memoria [ERROR]: RAM insuficiente para PID " + pid + ". Requiere " + needFrames + " marcos, disponibles " + freeFrames + "."
            );
            IPCBus.sendMessageToShell(error);

            //Avisarle al Kernel que mate al proceso.
            SystemMessage killProc = new SystemMessage(SystemMessage.Topic.PROCESS_KILL_REQUEST, 0, 0,
                    new String[]{"kill", String.valueOf(pid)}
            );
            IPCBus.sendMessageToKernel(killProc);
            return;
        }

        //Asignar los marcos el proceso toma control de los bloques.
        int asignedFrames = 0;
        for (int i = 0; i < TOTAL_FRAMES && asignedFrames < needFrames; i++) {
            if (memoryFrames[i] == 0) {
                memoryFrames[i] = pid; //"Pintamos" el bloque con el número del proceso.
                asignedFrames++;
            }
        }

        //Confirmar en la Shell.
        SystemMessage success = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                "Memoria: Reservados " + (asignedFrames * PAGE_SIZE_KB) + " KB (" + asignedFrames + " marcos) para el PID " + pid + "."
        );
        IPCBus.sendMessageToShell(success);
    }
}
