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
    private final String PROMPT = "user@minios:~$ ";
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
        
        // Iniciamos la terminal con el primer prompt impreso
        areaTrabajo.setText(PROMPT);
        // Colocamos el cursor al final del prompt
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

        // Volvemos a pintar el prompt para el siguiente comando
        areaTrabajo.append(PROMPT);
        
        // Actualizamos la posición para bloquear que borren el nuevo prompt
        areaTrabajo.setCaretPosition(areaTrabajo.getText().length());
        posicionEntradaUsuario = areaTrabajo.getText().length();
    }

    private String procesarTexto(String linea) {
        String[] tokens = linea.trim().split("\\s+");

        String comandoPrincipal = tokens[0].toLowerCase();
        switch (comandoPrincipal){
            case "ayuda":
                return "clear: Limpiar pantalla \nls: Mostrar directorio \ncd: Moverse al directorio";
                
            case "clear":
                areaTrabajo.setText("");
                return "";
                
            case "ls":
                return "Mostrando directorio";
                
            case "cd":
                return "Moviendo al directorio";
                
            default:
                return "Error: El comando '" + comandoPrincipal + "' no existe. usa comando [ayuda]";
        }
    }
}