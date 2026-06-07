package com.fractalos.ipc;

//Los módulos deben de implementar esta interfaz, a fin de estandarizar el método de comunicación con el kernel.
//Ademas deberán de implementar la interfaz Runnable y hacer @Override al método [public void run();] a fin de poder
//escuchar constantemente los mensajes recibidos por el package IPC.

public interface IPCModule {

    //Identificador del módulo. Útil para imprimir logs en consola y
    //hacer debugging (Ej. retorna "VFS_MODULE" o "MEMORY_MANAGER").
    String getModuleName();

    //Método principal de escucha.
    //Aquí es donde el módulo extraerá (take) el mensaje de su buzón
    //correspondiente en la clase IPCBus y ejecutará su lógica interna.
    void processMessage(SystemMessage msg);

    //Método de monitoreo.
    //Permite al Kernel saber si el módulo de usuario ya terminó de
    //inicializarse y está listo para recibir peticiones.
    boolean isActive();

    //Procedimiento de apagado seguro.
    //Se llama cuando el sistema operativo se está apagando (Shutdown)
    //para que el módulo cierre sus hilos y guarde sus estados.
    void shutDownModule();

}
