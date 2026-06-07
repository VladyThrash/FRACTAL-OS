package com.fractalos.ipc;
import java.util.concurrent.LinkedBlockingQueue;

//Esta clase maneja y contiene las colas de mensajes, que comunica los módulos con el kernel del sistema.
//Implementa LinkedBlockingQueue, lo que garantiza que múltiples hilos puedan leer/escribir al mismo tiempo sin
//corromper la memoria del sistema operativo simulado, si el buzón está saturado, no congelará al Kernel.
//Se utiliza offer() porque inserta el elemento de forma no bloqueante.

public class IPCBus {

    //Buzones (Mailboxes) específicos para cada subsistema en el Espacio de Usuario.
    public static final LinkedBlockingQueue<SystemMessage> memoryMailbox = new LinkedBlockingQueue<>();
    public static final LinkedBlockingQueue<SystemMessage> vfsMailbox = new LinkedBlockingQueue<>();
    public static final LinkedBlockingQueue<SystemMessage> shellMailbox = new LinkedBlockingQueue<>();

    //Envía un mensaje al Gestor de Memoria.
    public static void sendMessageToMemory(SystemMessage msg) {
        memoryMailbox.offer(msg);
    }

    //Envía un mensaje al sistema de Archivos Virtual (VFS).
    public static void sendMessageToVFS(SystemMessage msg) {
        vfsMailbox.offer(msg);
    }

    //Envía un mensaje a la Terminal (Shell).
    public static void sendMessageToShell(SystemMessage msg) {
        shellMailbox.offer(msg);
    }

    //Limpia todas las colas de mensajes.
    //Fundamental invocarlo desde el Kernel durante un Kernel Panic o apagado
    //del sistema (Shutdown) para liberar los objetos huérfanos en memoria.
    public static void clearComunicationBus() {
        memoryMailbox.clear();
        vfsMailbox.clear();
        shellMailbox.clear();
    }
}
