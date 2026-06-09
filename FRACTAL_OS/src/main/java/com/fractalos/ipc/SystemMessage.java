package com.fractalos.ipc;

//Esta clase estandariza el contexto o el contenido que es enviado por los módulos mediante el IPC.
//Para que exista comunicación entre ellos, forzosamente deben en encapsular los mensajes en esta clase.

public class SystemMessage {

    //El catálogo de eventos que el SO puede entender.
    public enum Topic {
        PROCESS_TERMINATED,        //El núcleo avisa que un proceso murió.
        PROCESS_CREATE_REQUEST,     //Del Shell al Kernel.
        PRINT_TO_CONSOLE,           //Del Kernel/VFS al Shell para imprimir en pantalla.
        CHANGE_FILE_PATH,            //Petición para cambiar el path mostrado en Shell.
        SYSTEM_SHUTDOWN_REQUEST,    //Petición de apagado total del sistema.
        PROCESS_LIST_REQUEST,       //Petición de listado de procesos activos.
        PROCESS_KILL_REQUEST,       //Petición para matar un proceso.
        PROCESS_PRIORITY_CHANGE_REQUEST, //Petición de cambio de prioridad.
        MEMORY_ALLOCATION_REQUEST,   //Petición para pedir RAM al iniciar un proceso.
        MEMORY_FREE_REQUEST,        //Petición para liberar RAM.
        MEMORY_MAP_REQUEST,         //Petición para dibujar el mapa de la RAM.
        VFS_CREATE_DIR_REQUEST,     //Petición para crear un nuevo directorio.
        VFS_LIST_REQUEST,           //Petición para listar los archivos del directorio actual.
        VFS_CHANGE_DIR_REQUEST,     //Petición para moverse entre directorios.
        VFS_CREATE_TEXT_REQUEST,    //Petición para crear un nuevo archivo de texto.
        VFS_CREATE_EXEC_REQUEST,    //Petición para crear un nuevo archivo ejecutable.
        VFS_READ_FILE_REQUEST,      //Petición para leer un archivo de texto.
        VFS_EXECUTE_REQUEST,        //Petición para arrancar un archivo ejecutable.
        VFS_DELETE_REQUEST          //Petición para borrar archivos y directorios.
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
