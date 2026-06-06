package com.fractalos.modules.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class Shell {
    private JFrame ventana;
    private JTextArea areaTrabajo;
    private String rutaActual = "user@minios:~$ ";
    private int posicionEntradaUsuario = 0; 

    public Shell() {
        ventana = new JFrame("FracShell");
        ventana.setSize(600, 400);
        ventana.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        ventana.setLayout(new BorderLayout());

        
        areaTrabajo = new JTextArea();
        areaTrabajo.setBackground(Color.BLACK);
        areaTrabajo.setForeground(Color.GREEN);
        areaTrabajo.setCaretColor(Color.WHITE);
        areaTrabajo.setFont(new Font("Consolas", Font.PLAIN, 14));
        
        // Iniciamos la terminal con el primer rutaActual impreso
        areaTrabajo.setText(rutaActual);
        // Colocamos el cursor al final del rutaActual
        areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
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
        // Añadimos un salto de línea manual ya que consumimos el ENTER original
        areaTrabajo.append("\n"); 

        if (!linea.trim().isEmpty()) {
            String respuesta = procesarTexto(linea);
            areaTrabajo.append(respuesta + "\n");
        }

        // Volvemos a pintar el rutaActual para el siguiente comando
        areaTrabajo.append(rutaActual);
        
        // Actualizamos la posición para bloquear que borren el nuevo rutaActual
        areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
        posicionEntradaUsuario = areaTrabajo.getText().length();
    }

    private String procesarTexto(String linea) {
        String[] tokens = linea.trim().split("\\s+");

        String comandoPrincipal = tokens[0].toLowerCase();
        switch(comandoPrincipal){
            case "ayuda":
                return ayudaInfo(tokens);
                
            case "clear":
            case "clr":
                areaTrabajo.setText("");
                return "";
                
            case "ls":
                return "Mostrando directorio";
                
            case "cd":
                return obtenerRutaActual(tokens);
                
            case "confs":
                return configuracionShell(tokens);
            default:
                return "Error: El comando '" + comandoPrincipal + "' no existe. usa comando [ayuda]";
        }
    }
    private String ayudaInfo(String[] tokens){
        if (tokens.length == 1){
            return "ayuda [comando] \nclear: Limpiar pantalla \nls: Mostrar directorio \ncd: Moverse al directorio \nconfs: Configuracion de terminal";
        }

        switch (tokens[1]) {
            case "ayuda":
                return "ayuda [comando]";
                
            case "cd":
                return "cd [directorio] -> Mueve terminal al directorio seleccionado";
                
            case "confs":
                return """
                    confs -s [tamaño] ->Cambia tamaño de fuente
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
                return "Tamaño de '"+ tokens[2] + "'' Establecido";
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
                        return "Error: Color no reconocido. Intenta con verde, blanco, azul o rojo.";
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
}