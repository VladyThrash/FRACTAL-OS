package com.fractalos.kernel;
import com.fractalos.ipc.IPCBus;
import com.fractalos.ipc.SystemMessage;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

//Para el planificador se utiliza el paquete java.util.concurrent.
//PriorityBlockQueue nos permite definir una cola de prioridad recurrente. Se crean instancias parametrizadas para
//Process (Implements Comparable) comparandose bajo su atributo <int priority>, manteniendo en la cima los procesos
//más importantes. Al ser recurrente permite encolar y desencolar procesos sin riesgo de corrupción de datos (Acceso mediante IPC).

public class Scheduler {

    //Creamos una instancia de la cola de prioridad. Atributo estatico de clase.
    public static final PriorityBlockingQueue<Process> queue = new PriorityBlockingQueue<>();

    //Método estático: Inserta un proceso (tanto nuevo como reencolado) a la cola de prioridad.
    public static boolean addProcess(Process p){
        return Scheduler.queue.add(p);
    }

    //Método estático: Consulta la cola y extrae el proceso con mayor prioridad.
    //Si la cola esta vacia, retorna null.
    public static Process getNextProcess(){
        return Scheduler.queue.poll();
    }

    //Método estático: Consulta la cola y obtiene el proceso sin extraerlo de la estructura.
    //Si la cola esta vacia, retorna null.
    public static Process peekNextProcess(){
        return Scheduler.queue.peek();
    }

    //Método estático: Consulta la cola para saber si se encuentra vacia y ya no quedan procesos por atender.
    public static boolean queueIsVoid(){
        return Scheduler.queue.isEmpty();
    }

    //Método estático: Busca un proceso en la cola de prioridad y lo elimina, utiliza Process ID.
    public static boolean deleteProcess(int pid){
        Iterator<Process> iterator = Scheduler.queue.iterator();
        while(iterator.hasNext()){
            Process p = iterator.next();
            if(p.getPid() == pid){
                iterator.remove();

                //Notificar mediante IPC liberación de memoria.
                SystemMessage msg = new SystemMessage(
                        SystemMessage.Topic.MEMORY_FREE_REQUEST,
                        0,          //sourcePid: 0 indica que el remitente es el Kernel.
                        p.getPid(),          //targetPid: El proceso que acaba de morir.
                        null
                );
                IPCBus.sendMessageToMemory(msg);

                return true;
            }
        }
        return false;
    }

    //Método estático: De la cola de prioridad, regresa una lista ordenada de todos los procesos en espera.
    public static List<Process> getAllProcesses(){
        return Scheduler.queue.stream().sorted().collect(Collectors.toList());
    }

    //Método estaico: Limpia o vacia toda la cola de prioridad.
    public static void clearAllProcesses(){
        Scheduler.queue.clear();
    }

}
