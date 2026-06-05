package com.fractalos.kernel;

//Bloque de control de proceso (PCB).
//Esta clase define y almacena toda la información de un hilo.
//Implementamos Runnable para que el ExecutorService (CPU) lo entienda.
//Implementamos Comparable para que la PriorityQueue (Scheduler) lo ordene.

public class Process  implements Runnable, Comparable<Process>{

    //Posibles estados en los que se puede encontrar un proceso.
    public enum STATE{
        NEW_PROCESS, //Nuevo
        READY_PROCESS, //Listo
        RUNNING_PROCESS, //En ejecución
        STANDBY_PROCESS, //En espera
        FINISHED_PROCESS //Terminado
    }

    //Atributos de la clase
    private final int pid;  //Process ID.
    private STATE actualState; //Estado actual del proceso
    private final int priority; //Nivel de prioridad en el planificador - despachador.
    private int remProcessing; //Tiempo de procesamiento restante del proceso para finalizar (ramaining processing).

    //Constructures
    public Process(int pid, int priority, int remProcessing){ //Para un proceso con prioridad definida.
        this.pid = pid;
        this.actualState = STATE.NEW_PROCESS;
        this.priority = priority;
        this.remProcessing = remProcessing;
    }

    public Process(int pid, int remProcessing){ //Para un proceso con una prioridad default.
        this.pid = pid;
        this.actualState = STATE.NEW_PROCESS;
        this.priority = 0; //Prioridad 0, termina en orden FIFO.
        this.remProcessing = remProcessing;
    }

    //Getters y Setters
    public int getPid() { //Obtener Process ID.
        return pid;
    }

    public STATE getActualState() { //Obtener el estado actual del proceso.
        return actualState;
    }

    public void setActualState(STATE actualState) { //Definir el estado actual del proceso.
        this.actualState = actualState;
    }

    public int getPriority() { //Obtener la prioridad del proceso.
        return priority;
    }

    public int getRemProcessing() {
        return remProcessing; //Obtener el tiempo de procesamiento restante del proceso.
    }

    public void setRemProcessing(int remProcessing) { //Definir el tiempo restante de procesamiento del proceso.
        this.remProcessing = remProcessing;
    }

    //Sobreecribimos el método de la interfaz funcional Runnable.
    @Override
    public void run() {
        this.setActualState(STATE.RUNNING_PROCESS);

        System.out.println("CPU ejecutando Proceso PID: " + this.pid);

        //Simulamos la ejecución del ciclo.
        while (remProcessing > 0) {
            // Simulamos trabajo y tiempo que toma un ciclo de CPU
            try {
                Thread.sleep(100); // 100ms por ciclo
            } catch (InterruptedException e) {
                // Si el planificador interrumpe el hilo (Expropiación/Preemption)
                this.setActualState(STATE.READY_PROCESS);
                System.out.println("Proceso PID: " + this.pid + " fue interrumpido.");
                return; // Sale de la CPU
            }
            remProcessing--;
        }

        this.setActualState(STATE.FINISHED_PROCESS);
        System.out.println("Proceso PID: " + this.pid + " finalizó su ráfaga.");
    }

    //Sobreescribimos el método de la clase funcional Comparable.
    @Override
    public int compareTo(Process otherProcess) {
        // Ordena de mayor a menor prioridad
        return Integer.compare(otherProcess.getPriority(), this.priority);
    }
}
