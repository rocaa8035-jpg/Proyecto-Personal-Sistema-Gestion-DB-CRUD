package views;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    private CardLayout cardLayout;
    private JPanel panelContenedor;

    public MainFrame() {
        setTitle("Sistema de Gestión de Datos - Java & MySQL (Simulado)");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Inicializar CardLayout y contenedor principal
        cardLayout = new CardLayout();
        panelContenedor = new JPanel(cardLayout);

        // Instanciar los formularios
        PersonaForm vPersona = new PersonaForm();
        ProductoForm vProducto = new ProductoForm();

        // Añadirlos al contenedor con un "nombre clave"
        panelContenedor.add(vPersona, "Módulo Personas");
        panelContenedor.add(vProducto, "Módulo Productos");

        // Crear Barra de Menú para cambiar de GUI
        JMenuBar menuBar = new JMenuBar();
        JMenu menuNavegacion = new JMenu("Cambiar Tabla / Módulo");
        
        JMenuItem itemPersonas = new JMenuItem("Gestionar Personas");
        JMenuItem itemProductos = new JMenuItem("Gestionar Productos");

        // Acciones del menú
        itemPersonas.addActionListener(e -> cardLayout.show(panelContenedor, "Módulo Personas"));
        itemProductos.addActionListener(e -> cardLayout.show(panelContenedor, "Módulo Productos"));

        menuNavegacion.add(itemPersonas);
        menuNavegacion.add(itemProductos);
        menuBar.add(menuNavegacion);
        
        setJMenuBar(menuBar);
        add(panelContenedor);
    }

    public static void main(String[] args) {
        // Ejecutar la aplicación aplicando el estilo del sistema operativo
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}