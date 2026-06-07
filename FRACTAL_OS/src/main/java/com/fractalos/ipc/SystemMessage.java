package com.fractalos.ipc;

//Esta clase estandariza el contexto o el contenido que es enviado por los módulos mediante el IPC.
//Para que exista comunicación entre ellos, forzosamente deben en encapsular los mensajes en esta clase.

public class SystemMessage {

    //El catálogo de eventos que el SO puede entender.
    public enum Topic {
        PROCESS_TERMINATED,        //El núcleo avisa que un proceso murió.
        MEMORY_ALLOCATION_REQUEST, //Petición para asignar páginas.
        MEMORY_FREE_REQUEST,       //Petición para liberar páginas.
        VFS_READ_REQUEST,          //Petición para leer archivo.
        VFS_WRITE_REQUEST,         //Petición para escribir archivo.
        SHELL_COMMAND_ISSUED,       //El usuario lanzó un comando en la terminal.
        PROCESS_CREATE_REQUEST,     //Del Shell al Kernel.
        PRINT_TO_CONSOLE,           //Del Kernel/VFS al Shell para imprimir en pantalla.
        SYSTEM_SHUTDOWN_REQUEST     //Petición de apagado total del sistema.
    }

    private final Topic topic;     //De qué trata el mensaje.
    private final int sourcePid;   //Quién lo envía (0 puede ser el Kernel).
    private final int targetPid;   //A qué proceso afecta.
    private final Object payload;  //Datos extra (Ej. un String con la ruta del archivo).

    public SystemMessage(Topic topic, int sourcePid, int targetPid, Object payload) {
        this.topic = topic;
        this.sourcePid = sourcePid;
        this.targetPid = targetPid;
        this.payload = payload;
    }

    //Getters inmutables (nadie debe modificar un mensaje en tránsito).
    public Topic getTopic() {
        return topic;
    }

    public int getSourcePid() {
        return sourcePid;
    }

    public int getTargetPid() {
        return targetPid;
    }

    public Object getPayload() {
        return payload;
    }

}
