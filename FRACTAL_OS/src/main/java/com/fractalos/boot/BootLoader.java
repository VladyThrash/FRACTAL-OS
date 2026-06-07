package com.fractalos.boot;

import com.fractalos.modules.shell.Shell;
import com.fractalos.ipc.IPCBus;
import com.fractalos.ipc.SystemMessage;
import com.fractalos.kernel.Process;
import com.fractalos.kernel.Scheduler;
import com.fractalos.kernel.Dispatcher;

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

        //Levantamos el "Demonio" del Kernel.
        //Este hilo representa el núcleo del microkernel escuchando el IPCBus.
        Thread hiloKernel = new Thread(() -> {
            System.out.println("Kernel iniciado. Escuchando peticiones IPC...");
            int contadorPid = 1; //Generador simple de identificadores de proceso.

            try {
                while (true) {
                    //El Kernel duerme (0% CPU) hasta que el Shell u otro módulo envíe una petición.
                    SystemMessage msg = IPCBus.kernelMailbox.take();

                    //Procesar la creación de un nuevo proceso.
                    if (msg.getTopic() == SystemMessage.Topic.PROCESS_CREATE_REQUEST) {

                        String[] tokens = (String[]) msg.getPayload();

                        //Parsear los argumentos enviados desde la terminal
                        //tokens[0] = "run", tokens[1] = prioridad, tokens[2] = ráfaga.
                        int prioridad = Integer.parseInt(tokens[1]);
                        int rafaga = Integer.parseInt(tokens[2]);

                        //Crear el Bloque de Control de Proceso (PCB).
                        Process nuevoProceso = new Process(contadorPid++, prioridad, rafaga);

                        //Encolarlo en el Planificador.
                        Scheduler.addProcess(nuevoProceso);

                        //Despachar el proceso inmediatamente si hay núcleos libres.
                        Process procesoSiguiente = Scheduler.getNextProcess();
                        if (procesoSiguiente != null) {
                            Dispatcher.dispatch(procesoSiguiente);
                        }

                        //Responder al usuario enviando un mensaje de vuelta al Shell.
                        SystemMessage respuesta = new SystemMessage(
                                SystemMessage.Topic.PRINT_TO_CONSOLE,
                                0, // 0 es el PID reservado para el Kernel
                                0,
                                "Kernel: Proceso PID [" + nuevoProceso.getPid() + "] creado y encolado correctamente."
                        );
                        IPCBus.sendMessageToShell(respuesta);
                    }

                    //Secuencia de apagado del sistema.
                    if (msg.getTopic() == SystemMessage.Topic.SYSTEM_SHUTDOWN_REQUEST) {
                        System.out.println("Secuencia de apagado iniciada por el usuario.");
                        //Mandamos mensaje de cierre a la Shell.
                        SystemMessage respuesta = new SystemMessage(
                                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                                "Kernel: Sistema detenido. Puede cerrar la ventana de forma segura."
                        );
                        IPCBus.sendMessageToShell(respuesta);
                        //Le damos tiempo a la interfaz gráfica (Swing) para que pinte el mensaje
                        //antes de cortar la energía abruptamente.
                        Thread.sleep(500);
                        //Ejecutamos tu método de limpieza profunda (Corta el ExecutorService y vacía colas)
                        Dispatcher.shutDown();
                        //Detenemos la Máquina Virtual de Java con código 0 (Apagado exitoso)
                        //Esto cerrará la ventana de Swing y matará todos los hilos restantes al instante.
                        System.exit(0);
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("Kernel interrumpido. Apagando el sistema...");
                Dispatcher.shutDown(); //Apagamos el ExecutorService.
                Thread.currentThread().interrupt();
            }
        });

        //Iniciamos el núcleo.
        hiloKernel.start();
    }
}