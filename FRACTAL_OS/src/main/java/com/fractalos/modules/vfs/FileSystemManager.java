package com.fractalos.modules.vfs;
import com.fractalos.ipc.IPCModule;
import com.fractalos.ipc.IPCBus;
import com.fractalos.ipc.SystemMessage;

//Al igual que KernelDaemon y MemoryManager, FileSystemManager es un hilo (Daemon) independiente, por ello implementa Runnable.
//Contrata IPCModule para recibir y enviar peticiones a otros módulos del sistema. Se simula un sistema de archivos utilizando
//un Árbol N-ario, donde cada nodo es la clase FileNode que contiene la métadata del archivo, por ejemplo, se define si el
//nodo es un directorio (puede contener otros archivos y directorios) o si es un archivo simple (lectura o ejecutable).

public class FileSystemManager implements IPCModule, Runnable {
    private boolean active = false;
    private final FileNode root; //Nodo raíz.
    private FileNode currentDir; //Puntero al nodo actual.

    public FileSystemManager() { //Para hacer la instancia del hilo e inicializar en root.
        this.root = new FileNode("root", null);
        this.currentDir = root;
    }

    //Implementación del los métodos del contrato IPCModule.
    @Override
    public String getModuleName() {
        return "FRACTAL_VFS_MANAGER";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void shutDownModule() {
        this.active = false;
        System.out.println("Módulo VFS apagado.");
    }

    @Override
    public void processMessage(SystemMessage msg) {
        switch (msg.getTopic()) {
            case VFS_LIST_REQUEST:
                listDir();
                break;
            case VFS_CREATE_DIR_REQUEST:
                makeDir((String[]) msg.getPayload());
                break;
            case VFS_CHANGE_DIR_REQUEST:
                changeDir((String[]) msg.getPayload());
                break;
            case VFS_CREATE_TEXT_REQUEST:
                makeTextFile((String[]) msg.getPayload());
                break;
            case VFS_CREATE_EXEC_REQUEST:
                makeExecFile((String[]) msg.getPayload());
                break;
            case VFS_READ_FILE_REQUEST:
                readFile((String[]) msg.getPayload());
                break;
            case VFS_EXECUTE_REQUEST:
                execFile((String[]) msg.getPayload());
            default:
                break;
        }
    }

    //Implementamos el Runnable (motor del hilo).
    @Override
    public void run() {
        this.active = true;
        System.out.println("Gestor VFS iniciado. Árbol de directorios montado.");
        try {
            while (active) {
                SystemMessage msg = IPCBus.vfsMailbox.take();
                processMessage(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutDownModule();
        }
    }

    //Petición: Lista todos los archivos contenidos en el directorio actual
    private void listDir() {
        StringBuilder list = new StringBuilder();
        list.append("\n--- Contenido de ~/").append(currentDir.getName()).append(" ---\n");

        if (currentDir.getChildren().isEmpty()) {
            list.append("(Directorio vacío)\n");
        } else {
            for (FileNode nodo : currentDir.getChildren().values()) {
                if (nodo.getType() == FileNode.Type.DIRECTORY) {
                    list.append(String.format("[DIR]  %-15s | %d KB\n", nodo.getName(), nodo.getSizeKB()));
                } else {
                    list.append(String.format("[FILE] %-15s | %d KB\n", nodo.getName(), nodo.getSizeKB()));
                }
            }
        }

        //Devolvemos el texto a la terminal.
        SystemMessage msg = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, list.toString()
        );
        IPCBus.sendMessageToShell(msg);
    }

    //Petición: Crear un nuevo directorio en la dirección actual.
    private void makeDir(String[] tokens) {
        if (tokens.length < 2) {
            sendError("Uso correcto: mkdir [nombre_carpeta]");
            return;
        }
        String nameDir = tokens[1];

        //Validar que no exista ya algo con ese nombre.
        if (currentDir.getChild(nameDir) != null) {
            sendError("Ya existe un archivo o directorio llamado '" + nameDir + "'.");
            return;
        }

        //Validar que no se cree un directorio con nombre: ".." (Retorno para [cd ..]).
        if(nameDir.equals("..")){
            sendError("No se puede crear un archivo con ese nombre.");
            return;
        }

        //Creamos la carpeta y la colgamos del árbol.
        FileNode newDir = new FileNode(nameDir, currentDir);
        currentDir.addChild(newDir);

        SystemMessage msg = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                "VFS: Directorio '" + nameDir + "' creado."
        );
        IPCBus.sendMessageToShell(msg);
    }

    //Petición: Moverse al directorio que fue pasado como argumento.
    private void changeDir(String[] tokens) {
        if (tokens.length < 2) {
            sendError("Uso correcto: cd [nombre_carpeta o ..]");
            return;
        }
        String destiny = tokens[1];

        //Lógica para subir un nivel (Comando "cd ..").
        if (destiny.equals("..")) {
            if (currentDir.getParent() != null) {
                currentDir = currentDir.getParent();
                sendNewPath(); //Indicamos al Shell un cambio en el path.
            } else {
                sendError("Ya estás en el directorio raíz.");
            }
            return;
        }

        //Lógica para entrar a una carpeta hija.
        FileNode destinyNode = currentDir.getChild(destiny);
        if (destinyNode != null && destinyNode.getType() == FileNode.Type.DIRECTORY) {
            currentDir = destinyNode;
            sendNewPath(); //Indicamos al Shell un cambio en el path.
        } else {
            sendError("El directorio '" + destiny + "' no existe o no es una carpeta.");
        }
    }

    //Petición: Crear un nuevo archivo de texto.
    private void makeTextFile(String[] tokens) {
        if (tokens.length < 3) {
            sendError("Uso: nano [nombre] [contenido]");
            return;
        }
        String name = tokens[1];
        String content = tokens[2];

        //Verificar previa existencia.
        if (currentDir.getChild(name) != null) {
            sendError("Ya existe algo llamado '" + name + "'.");
            return;
        }

        currentDir.addChild(new FileNode(name, content, currentDir)); //Añadimos como hijo.
        SystemMessage msg = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                "VFS: Archivo de texto '" + name + "' guardado."
        );
        IPCBus.sendMessageToShell(msg);
    }

    //Petición: Crear un nuevo archivo ejecutable.
    private void makeExecFile(String[] tokens) {
        if (tokens.length < 5) {
            sendError("Uso: touch [nombre] [prioridad] [rafagas] [memoria_KB]");
            return;
        }
        //Blindaje contra tipos que no sean Integer.
        try {
            String name = tokens[1];
            int prio = Integer.parseInt(tokens[2]);
            int rfg = Integer.parseInt(tokens[3]);
            int ram = Integer.parseInt(tokens[4]);

            if (currentDir.getChild(name) != null) {
                sendError("Ya existe algo llamado '" + name + "'.");
                return;
            }

            currentDir.addChild(new FileNode(name, prio, rfg, ram, currentDir)); //Añadimos como hijo.
            SystemMessage msg = new SystemMessage(
                    SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                    "VFS: Ejecutable '" + name + "' compilado."
            );
            IPCBus.sendMessageToShell(msg);
        } catch (NumberFormatException e) {
            sendError("Los parámetros de ejecución deben ser números enteros.");
        }
    }

    //Petición: Leer un archivo de texto (mostrar el contenido en la Shell).
    private void readFile(String[] tokens) {
        if (tokens.length < 2) {
            sendError("Uso: open [nombre]"); return;
        }

        //Verificación de existencia.
        FileNode node = currentDir.getChild(tokens[1]);
        if (node == null) {
            sendError("El archivo no existe.");
            return;
        }

        //Verificación de tipo.
        if (node.getType() != FileNode.Type.TEXT_FILE) {
            sendError("No se puede abrir. No es un archivo de texto.");
            return;
        }

        //Devolvemos el texto a la pantalla.
        String output = "\n--- Abriendo " + node.getName() + " ---\n" + node.getContent() + "\n--------------------";
        SystemMessage msg = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, output
        );
        IPCBus.sendMessageToShell(msg);
    }

    //Petición: Inicializar el proceso de un archivo ejecutable.
    private void execFile(String[] tokens) {
        if (tokens.length < 2) {
            sendError("Uso: run [nombre_ejecutable]");
            return;
        }

        //Verificación de existencia.
        FileNode node = currentDir.getChild(tokens[1]);
        if (node == null) {
            sendError("El archivo no existe.");
            return;
        }

        //Verificación de tipo.
        if (node.getType() != FileNode.Type.EXECUTABLE) {
            sendError("Permiso denegado. Este archivo no es un programa ejecutable.");
            return;
        }

        //Extraemos los metadatos y construimos la petición al Kernel para generar su PCB.
        String[] toKernel = {
                "run",
                String.valueOf(node.getPriority()),
                String.valueOf(node.getBursts()),
                String.valueOf(node.getRequiredRamKB())
        };

        //Mensaje para la Shell.
        SystemMessage msgShell = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0,
                "VFS: Cargando proceso '" + node.getName() + "'..."
        );
        IPCBus.sendMessageToShell(msgShell);

        //Petición para el kernel.
        SystemMessage msgKernel = new SystemMessage(SystemMessage.Topic.PROCESS_CREATE_REQUEST, 0, 0, toKernel);
        IPCBus.sendMessageToKernel(msgKernel);
    }

    //Método recursivo para armar la ruta completa escalando hacia el padre.
    private String builtAbsolutePath(FileNode node) {
        if (node.getParent() == null) {
            return "~"; //El directorio raíz.
        }
        return builtAbsolutePath(node.getParent()) + "/" + node.getName();
    }

    //Empaquetar la ruta y enviarla a la Shell.
    private void sendNewPath() {
        String fullPath = builtAbsolutePath(currentDir);
        String newPrompt = "user@fractal:" + fullPath + "$ ";

        SystemMessage msg = new SystemMessage(
                SystemMessage.Topic.CHANGE_FILE_PATH,
                0, 0, newPrompt
        );
        IPCBus.sendMessageToShell(msg);
    }

    //Método: Enviar mensaje de error a la Shell.
    private void sendError(String mensaje) {
        SystemMessage msg = new SystemMessage(
                SystemMessage.Topic.PRINT_TO_CONSOLE, 0, 0, "VFS [ERROR]: " + mensaje
        );
        IPCBus.sendMessageToShell(msg);
    }
}
