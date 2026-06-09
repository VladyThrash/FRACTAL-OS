package com.fractalos.modules.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.fractalos.ipc.IPCModule;
import com.fractalos.ipc.IPCBus;
import com.fractalos.ipc.SystemMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.HashMap;

public class Shell implements IPCModule, Runnable{
    private JFrame ventana;
    private JTextArea areaTrabajo;
    private String rutaActual = "user@fractal:~$ ";
    private int posicionEntradaUsuario = 0;
    private boolean active = false; //Atributo utilizado por el contrato IPCModule.
    private boolean esperandoConfirmacionApagado = false;
    private boolean isLoggedIn = false;
    private int loginStep = 0; // 0 = Pidiendo Usuario, 1 = Pidiendo Password
    private String tempUser = "";
    private Map<String, String> shadowData;
    private static final String SHADOW_FILE = "shadow.fractal";

    public Shell() {
        ventana = new JFrame("FracShell");
        ventana.setSize(600, 400);
        ventana.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        ventana.setLayout(new BorderLayout());

        areaTrabajo = new JTextArea();
        areaTrabajo.setBackground(Color.BLACK);
        areaTrabajo.setForeground(Color.GREEN);
        areaTrabajo.setCaretColor(Color.WHITE);
        areaTrabajo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        //Cargamos la bóveda de usuarios (o creamos los por defecto).
        this.shadowData = cargarShadow();

        //Hook de apagado: Guarda la bóveda al salir del sistema.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            guardarShadow();
        }));

        //Cambiamos el estado inicial a modo login e imprimimos el logo.
        this.rutaActual = "login: ";
        imprimirPantallaLogin();

        posicionEntradaUsuario = areaTrabajo.getText().length();

        JScrollPane scroll = new JScrollPane(areaTrabajo);
        ventana.add(scroll, BorderLayout.CENTER);

        // Capturar eventos del teclado directamente en la terminal
        areaTrabajo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int posicionActualCursor = areaTrabajo.getCaretPosition();

                if(e.isActionKey() || e.isControlDown() || e.getKeyCode() == KeyEvent.VK_SHIFT){
                    return;
                }

                if(e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyCode() == KeyEvent.VK_DELETE){
                    if(posicionActualCursor <= posicionEntradaUsuario){
                        e.consume();
                        return;
                    }
                }else if(e.getKeyCode() == KeyEvent.VK_ENTER){
                    e.consume();
                    String textoCompleto = areaTrabajo.getText();
                    String comando = "";
                    if(textoCompleto.length() >= posicionEntradaUsuario){
                        comando = textoCompleto.substring(posicionEntradaUsuario);
                    }
                    if (!isLoggedIn) {
                        if (loginStep == 0) { // Validando Usuario
                            tempUser = comando;
                            if (tempUser.isEmpty()) {
                                areaTrabajo.append("\n" + rutaActual);
                                posicionEntradaUsuario = areaTrabajo.getText().length();
                                return;
                            }
                            rutaActual = "password: ";
                            loginStep = 1;
                            areaTrabajo.append("\n" + rutaActual);

                        } else if (loginStep == 1) { // Validando Contraseña
                            String passwordAttempt = comando;
                            if (shadowData.containsKey(tempUser) && shadowData.get(tempUser).equals(passwordAttempt)) {
                                // ¡ACCESO CONCEDIDO!
                                isLoggedIn = true;
                                rutaActual = tempUser + "@fractal:~/$ ";
                                areaTrabajo.append("\n\n[ OK ] Autenticación exitosa.");
                                areaTrabajo.append("\nBienvenido a FRACTAL-OS.\n\n" + rutaActual);
                            } else {
                                // ACCESO DENEGADO
                                areaTrabajo.append("\n[ ERROR ] Login incorrecto.");
                                loginStep = 0;
                                rutaActual = "login: ";
                                areaTrabajo.append("\n\n" + rutaActual);
                            }
                        }
                        areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
                        posicionEntradaUsuario = areaTrabajo.getText().length();
                        return; // Cortamos la ejecución. NUNCA llega a ejecutarComando().
                    }
                    ejecutarComando(comando);
                    return;
                }else{
                    if(posicionActualCursor < posicionEntradaUsuario){
                        areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
                    }
                }

            }
        });
    }

    public void mostrar() {
        ventana.setVisible(true);
        areaTrabajo.requestFocusInWindow(); // Pone el foco directamente en la terminal al abrirse
    }

    private void ejecutarComando(String linea) {
        areaTrabajo.append("\n");

        if (!linea.trim().isEmpty()) {
            String respuesta = procesarTexto(linea);
            areaTrabajo.append(respuesta + "\n");
        }

        areaTrabajo.append(rutaActual);
        areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
        posicionEntradaUsuario = areaTrabajo.getText().length();
    }

    private String procesarTexto(String linea) {
        //Máquina de estados pára confirmación de shutdown.
        String input = linea.trim().toLowerCase();
        if (esperandoConfirmacionApagado) {
            esperandoConfirmacionApagado = false;
            if (input.equals("s")) {
                //El usuario confirmó. Mandamos la orden al núcleo.
                SystemMessage peticion = new SystemMessage(
                        SystemMessage.Topic.SYSTEM_SHUTDOWN_REQUEST,
                        0, 0, null
                );
                IPCBus.sendMessageToKernel(peticion);
                return "Iniciando secuencia de apagado... Cerrando módulos.";
            } else {
                return "Apagado cancelado.";
            }
        }
        String[] tokens = linea.split(" ");
        String comandoPrincipal = tokens[0].toLowerCase();

        switch(comandoPrincipal){
            case "test-proc":
                SystemMessage peticion = new SystemMessage(
                        SystemMessage.Topic.PROCESS_CREATE_REQUEST,
                        0, 0, tokens //Enviamos los argumentos al Kernel.
                );
                IPCBus.sendMessageToKernel(peticion);
                return "Petición enviada al Kernel...";

            case "shutdown":
                esperandoConfirmacionApagado = true;
                return "ADVERTENCIA: ¿Estás seguro que deseas apagar FRACTAL-OS? (s/n): ";

            case "ps":
                SystemMessage peticionPS = new SystemMessage(
                        SystemMessage.Topic.PROCESS_LIST_REQUEST,
                        0, 0, null
                );
                IPCBus.sendMessageToKernel(peticionPS);
                return "Consultando Tabla Global de Procesos...";

            case "kill":
                if (tokens.length < 2) {
                    return "Error: Falta el PID. Uso: kill [pid]";
                }
                SystemMessage peticionKill = new SystemMessage(
                        SystemMessage.Topic.PROCESS_KILL_REQUEST,
                        0, 0, tokens
                );
                IPCBus.sendMessageToKernel(peticionKill);
                return "Enviando señal KILL al proceso " + tokens[1] + "...";

            case "renice":
                if (tokens.length < 3) {
                    return "Error: Faltan argumentos. Uso: renice [pid] [priority]";
                }
                SystemMessage peticionRenice = new SystemMessage(
                        SystemMessage.Topic.PROCESS_PRIORITY_CHANGE_REQUEST,
                        0, 0, tokens
                );
                IPCBus.sendMessageToKernel(peticionRenice);
                return "Solicitando cambio de prioridad para el PID " + tokens[1] + "...";

            case "mem":
                SystemMessage peticionMapeoMem = new SystemMessage(
                    SystemMessage.Topic.MEMORY_MAP_REQUEST,
                    0, 0, tokens
                );
                IPCBus.sendMessageToMemory(peticionMapeoMem);
                return "Consultando mapeo de RAM...";

            case "ayuda":
                return ayudaInfo(tokens);

            case "clear":
            case "clr":
                areaTrabajo.setText("");
                return "";

            case "ls":
                SystemMessage peticionLS = new SystemMessage(
                        SystemMessage.Topic.VFS_LIST_REQUEST, 0, 0, tokens
                );
                IPCBus.sendMessageToVFS(peticionLS);
                return "Listando directorio...";

            case "mkdir":
                SystemMessage peticionMKDIR = new SystemMessage(
                        SystemMessage.Topic.VFS_CREATE_DIR_REQUEST, 0, 0, tokens
                );
                IPCBus.sendMessageToVFS(peticionMKDIR);
                return "Creando directorio...";

            case "cd":
                SystemMessage peticionCD = new SystemMessage(
                        SystemMessage.Topic.VFS_CHANGE_DIR_REQUEST, 0, 0, tokens
                );
                IPCBus.sendMessageToVFS(peticionCD);
                return "";

            case "touch":
                SystemMessage peticionTouch = new SystemMessage(
                        SystemMessage.Topic.VFS_CREATE_EXEC_REQUEST, 0, 0, tokens
                );
                IPCBus.sendMessageToVFS(peticionTouch);
                return "";

            case "open":
                SystemMessage peticionOpen = new SystemMessage(
                        SystemMessage.Topic.VFS_READ_FILE_REQUEST, 0, 0, tokens
                );
                IPCBus.sendMessageToVFS(peticionOpen);
                return "";

            case "run":
                SystemMessage peticionRun = new SystemMessage(
                        SystemMessage.Topic.VFS_EXECUTE_REQUEST, 0, 0, tokens
                );
                IPCBus.sendMessageToVFS(peticionRun);
                return "";

            case "nano":
                //Como el contenido del texto tiene espacios, separamos en máximo 3 partes.
                String[] nanoTokens = linea.split(" ", 3);
                //Limpiamos las comillas.
                if(nanoTokens.length == 3) {
                    nanoTokens[2] = nanoTokens[2].replace("\"", "");
                }
                SystemMessage peticionNano = new SystemMessage(
                        SystemMessage.Topic.VFS_CREATE_TEXT_REQUEST, 0, 0, nanoTokens
                );
                IPCBus.sendMessageToVFS(peticionNano);
                return "";

            case "rm":
                SystemMessage peticionRM = new SystemMessage(
                        SystemMessage.Topic.VFS_DELETE_REQUEST, 0, 0, tokens
                );
                IPCBus.sendMessageToVFS(peticionRM);
                return "";

            case "useradd":
                if (tokens.length < 3) {
                    return "Uso correcto: useradd [nuevo_usuario] [contraseña]";
                } else {
                    if (!tempUser.equals("admin")) {
                        return "Permiso denegado. Solo el usuario 'admin' puede crear cuentas.";
                    } else {
                        String nuevoUsuario = tokens[1];
                        String nuevaClave = tokens[2];
                        if (shadowData.containsKey(nuevoUsuario)) {
                            return "Error: El usuario '" + nuevoUsuario + "' ya existe.";
                        } else {
                            shadowData.put(nuevoUsuario, nuevaClave);
                            return "Usuario '" + nuevoUsuario + "' creado exitosamente.";
                        }
                    }
                }

            case "confs":
                return configuracionShell(tokens);
            default:
                return "Error: El comando '" + comandoPrincipal + "' no existe. usa comando [ayuda]";
        }
    }
    private String ayudaInfo(String[] tokens){
        if (tokens.length == 1){
            return """
                ayuda -comando
                clear: Limpiar pantalla. 
                ls: Mostrar directorio.
                cd [dir]: Moverse al directorio. 
                mkdir [name]: Crear un directorio en la dirección actual.
                nano [name][content]: Crear un archivo de texto.
                open [name]: Leer un archivo de texto.
                touch [nombre] [prioridad] [rafagas] [memoria_KB]: Crear exec.
                run [nombre]: Inicializar proceso de un archivo ejecutable.
                rm [nombre]: Borrar archivo del directorio actual.
                test-proc [prioridad] [rafagas] [memoria]: Testear un proceso.
                shutdown: Apagar el sistema de forma segura.
                ps: Lista todos los procesos vivos encolados.
                kill [pid]: Mata o fuerza la terminación de un proceso activo.
                renice [pid] [prioridad]: Cambia la prioridad de un proceso activo.
                mem: Mapeo de la memoria RAM.
                useradd [nuevo_usuario] [contraseña]: Añadir un nuevo usuario al sistema.
                confs: Configuracion de terminal
                """;
        }

        switch (tokens[1]) {
            case "ayuda":
                return "ayuda [comando]";

            case "cd":
                return "cd [directorio] -> Mueve terminal al directorio seleccionado";

            case "confs":
                return """
                    confs -s -numero [tamaño] ->Cambia tamaño de fuente
                    confs -c -color [verde|blanco|azul|rojo] -> Cambia color de la fuente""";
            default:
                return "Comando desconocido";
        }
    }
    private String configuracionShell(String[] tokens){
        if(tokens.length < 2){
            return "Error: Faltan argumentos consula 'ayuda conf'";
        }
        String bandera = tokens[1].toLowerCase();

        switch(bandera){
            case "-s":
                if(tokens.length <3 ){ return "Error: Debes especificar un tamaño numérico. Ej: confs -fs 16";}
                try{
                    int nuevoTam = Integer.parseInt(tokens[2]);
                    if(nuevoTam < 10 || nuevoTam > 40){
                        return "Error: El tamaño debe ser entre 10 y 40";
                    }

                    Font fuenteActual = areaTrabajo.getFont();
                    areaTrabajo.setFont(new Font(fuenteActual.getName(), fuenteActual.getStyle(),nuevoTam));
                }catch(NumberFormatException e){
                    return "Error: '"+tokens[2]+"' No es numero valido";
                }
                return "Tamaño de '"+ tokens[2] + "' Establecido";
            case "-c":
                if (tokens.length < 3) {
                    return "Error: Especifica un color. Opciones: verde, blanco, azul, rojo";
                }
                String colorSelec = tokens[2].toLowerCase();

                switch (colorSelec) {
                    case "verde":
                        areaTrabajo.setForeground(Color.GREEN);
                        return "Color de fuente cambiado a Verde.";
                    case "blanco":
                        areaTrabajo.setForeground(Color.WHITE);
                        return "Color de fuente cambiado a Blanco.";
                    case "azul":
                        areaTrabajo.setForeground(new Color(50, 150, 255)); // Azul brillante más legible
                        return "Color de fuente cambiado a Azul.";
                    case "rojo":
                        areaTrabajo.setForeground(Color.RED);
                        return "Color de fuente cambiado a Rojo.";
                    default:
                        return "Error: Color no reconocido. verde, blanco, azul o rojo.";
                }
            default:
                return "Error: Parámetro '" + bandera + "' no válido. Usa 'ayuda confs'.";
        }
    }

    private String obtenerRutaActual(String[] tokens){
        //String rutaActual = gestorArchivos.getRuta();
        //return usuario + rutaActual +"$ ";
        rutaActual = tokens[1] + ":~$ ";
        return "moviendo al directorio: " + tokens[1];
    }

    //Métodos de comunicación con bus IPC.
    @Override
    public String getModuleName() {
        return "FRACTAL_SHELL";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void shutDownModule() {
        active = false;
        ventana.dispose(); //Cierra la ventana de Swing.
    }

    @Override
    public void processMessage(SystemMessage msg) {
        if (msg.getTopic() == SystemMessage.Topic.PRINT_TO_CONSOLE) {
            String textoAImprimir = (String) msg.getPayload();

            //Usamos invokeLater para delegar la actualización visual al hilo de Swing.
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    areaTrabajo.append("\n" + textoAImprimir + "\n" + rutaActual);
                    areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
                    posicionEntradaUsuario = areaTrabajo.getText().length();
                }
            });
        } else if (msg.getTopic() == SystemMessage.Topic.CHANGE_FILE_PATH) {
            String nuevaRuta = (String) msg.getPayload();

            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    rutaActual = tempUser + nuevaRuta; //Actualizamos la memoria de la Shell.
                    areaTrabajo.append("\n" + rutaActual);
                    areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
                    posicionEntradaUsuario = areaTrabajo.getText().length();
                }
            });
        }
    }

    @Override
    public void run() {
        this.active = true;
        mostrar();

        try {
            while (active) {
                //El Shell se queda dormido aquí hasta que alguien le envíe un texto para imprimir.
                SystemMessage msg = IPCBus.shellMailbox.take();
                processMessage(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> cargarShadow() {
        File archivoShadow = new File(SHADOW_FILE);
        if (archivoShadow.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(archivoShadow))) {
                return (Map<String, String>) ois.readObject();
            } catch (Exception e) {
                System.err.println("Shell [ERROR]: Archivo shadow corrupto. Creando nueva bóveda...");
            }
        }
        Map<String, String> nuevaBoveda = new HashMap<>();
        nuevaBoveda.put("user", "1234");
        nuevaBoveda.put("admin", "admin");
        return nuevaBoveda;
    }

    private void guardarShadow() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SHADOW_FILE))) {
            oos.writeObject(this.shadowData);
            System.out.println("Shell: Bóveda de credenciales guardada exitosamente.");
        } catch (Exception e) {
            System.err.println("Shell [ERROR]: No se pudo guardar el archivo shadow.");
        }
    }

    private void imprimirPantallaLogin() {
        String neofetch =
                "\n" +
                        "        ===*===            OS: FRACTAL-OS v1.0\n" +
                        "      -====*====-          Kernel: Fractal Microkernel\n" +
                        "    ====:  *  :====        Arch: Java Virtual Machine\n" +
                        "   :===.   *   .===:       Shell: FracShell\n" +
                        "  -===     *     ===-      Memory: 1024 KB Paged\n" +
                        "   :===.   *   .===:       CPU: 4 Virtual Cores\n" +
                        "    ====:  *  :====        VFS: Persistent Tree\n" +
                        "      -====*====-          \n" +
                        "        ===*===            \n" +
                        "\n" +
                        rutaActual;

        areaTrabajo.setText(neofetch);
        areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
        posicionEntradaUsuario = areaTrabajo.getText().length();
    }

}