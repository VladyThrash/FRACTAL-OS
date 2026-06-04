# FRACTAL-OS

**FRACTAL-OS** es un proyecto de sistema operativo con un enfoque académico altamente experimental. Su diseño propone un cambio de paradigma al utilizar la Java Virtual Machine (JVM) como una Capa de Abstracción de Hardware rápida y segura, implementando una arquitectura orientada a Microkernel.

---

## Arquitectura y Estructura del Proyecto

El proyecto está estrictamente desacoplado para simular la separación de privilegios y evitar conflictos durante el desarrollo colaborativo. Todo el código fuente reside bajo `src/main/java/com/fractalos/`:

* **`boot/`**: Contiene el punto de entrada principal del sistema. Es el primer proceso en ejecutarse y se encarga de instanciar las colas de mensajes (IPC), arrancar el kernel y lanzar los procesos del espacio de usuario.
* **`ipc/`** *(Bus de Comunicación)*: Actúa como el puente del sistema. Contiene las interfaces y clases para el paso de mensajes. El núcleo y los módulos de usuario interactúan estrictamente mediante este paquete.
* **`kernel/`** *(Espacio del Núcleo)*: Mantiene el núcleo base lo más reducido posible.
    * `Planificador` (Scheduler): Organiza la cola de procesos.
    * `Despachador`: Asigna la ejecución simulada.
* **`modules/`** *(Espacio de Usuario)*: Contiene los servicios de nivel superior que se ejecutan como hilos aislados:
    * `memory/`: Gestor lógico de memoria o paginador.
    * `shell/`: Interfaz de línea de comandos (Terminal).
    * `vfs/`: Sistema de archivos virtual.

---

## Flujo de Trabajo Colaborativo

Para mantener la integridad de la arquitectura de Microkernel y minimizar los *merge conflicts*:
1.  **Aislamiento por Dominios:** Asignen el desarrollo basándose en los paquetes. Si alguien trabaja en el gestor de memoria, debe limitar sus cambios al directorio `modules/memory/`.
2.  **Respetar el Diseño Base:** Ninguna clase en `kernel/` debe importar directamente desde `modules/`. Toda comunicación debe fluir a través de las abstracciones definidas.
3.  **Ramas de Desarrollo:** Creen ramas con prefijos claros según el dominio, por ejemplo: `feat/scheduler`, `fix/vfs-lectura`, `core/ipc-queue`.

---
