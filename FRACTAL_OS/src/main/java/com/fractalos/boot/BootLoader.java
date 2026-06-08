package com.fractalos.boot;

import com.fractalos.modules.shell.Shell;
import com.fractalos.kernel.KernelDaemon;
import com.fractalos.modules.memory.MemoryManager;
import com.fractalos.modules.vfs.FileSystemManager;

public class BootLoader {
    public static void main(String args[]) {
        System.out.println("Secuencia de arranque de FRACTAL-OS iniciada...");

        //Instanciamos el módulo Shell.
        Shell miterminal = new Shell();

        //Levantamos la interfaz gráfica en el hilo de eventos de Swing.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                miterminal.mostrar();
            }
        });

        //Arrancamos el motor de escucha del Shell en un hilo independiente.
        Thread hiloShell = new Thread(miterminal);
        hiloShell.start();

        //Levantamos el Demonio del Kernel.
        //Este hilo representa el núcleo del microkernel escuchando el IPCBus.
        KernelDaemon demonio = new KernelDaemon();
        Thread hiloKernel = new Thread(demonio);
        hiloKernel.start();

        //Levantamos el Demonio del Gestor de Memoria.
        //MemoryManager espera las peticiones del sistema.
        MemoryManager memory = new MemoryManager();
        Thread hiloMemoria = new Thread(memory);
        hiloMemoria.start();

        //Levantamos el Demonio del VFS.
        //FileSystemManager espera las peticiones del sistema.
        FileSystemManager files = new FileSystemManager();
        Thread hiloArchivos = new Thread(files);
        hiloArchivos.start();
    }
}