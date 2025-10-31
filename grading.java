/* grading.java
   Requires: JFreeChart jars added to your project's libraries.
   Usage: javac grading.java
          java grading
*/

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.text.DecimalFormat;

// JFreeChart imports
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.ui.RectangleAnchor;


public class grading {

    // CSV file path
    private static final String CSV_FILE = "gradeinput.csv";

    // Global map for fixed category weights (Major 60%, Minor 40%)
    private static final Map<String, Double> WEIGHT_CONFIG = Map.of(
            "Major", 60.0,
            "Minor", 40.0
    );
    // Key set for category dropdown
    private static final String[] CATEGORIES = WEIGHT_CONFIG.keySet().toArray(new String[0]);


    // Stores grades: Map<SubjectName, List<GradeItem>>
    private static Map<String, List<GradeItem>> allGrades = new TreeMap<>();

    // Main components
    private static JFrame mainFrame;
    private static JTextArea resultArea;
    private static JComboBox<String> subjectDropdown;

    public static void main(String[] args) {
        // Set System Look and Feel for a native appearance (which is typically light)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set System Look and Feel. Falling back to default.");
        }

        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    /**
     * Data structure to hold a single grade item.
     */
    static class GradeItem {
        String name;
        String subject;
        String category;
        int score;
        int outOf;
        String date;

        GradeItem(String name, String subject, String category, int score, int outOf, String date) {
            this.name = name;
            this.subject = subject;
            this.category = category;
            this.score = score;
            this.outOf = outOf;
            this.date = date;
        }

        String toCSV() {
            return String.format("%s,%s,%s,%d,%d,%s",
                    name, subject, category, score, outOf, date);
        }
    }

    private static void createAndShowGUI() {
        mainFrame = new JFrame("Grade Management System");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());

        // Ensure the main content pane uses the system's light background color
        mainFrame.getContentPane().setBackground(UIManager.getColor("control"));

        // Load data on startup
        loadGrades();

        // --- Menu Bar ---
        JMenuBar menuBar = new JMenuBar();
        JMenu dataMenu = new JMenu("Data");

        JMenuItem addGradeItem = new JMenuItem("Add Grade Item");
        addGradeItem.addActionListener(e -> showGradeInputDialog());

        JMenuItem viewChartItem = new JMenuItem("View Progress Chart");
        viewChartItem.addActionListener(e -> showChart());

        JMenuItem saveItem = new JMenuItem("Save Data");
        saveItem.addActionListener(e -> saveGrades());

        JMenuItem configureWeightsItem = new JMenuItem("View Category Weights");
        configureWeightsItem.setEnabled(true);
        configureWeightsItem.addActionListener(e -> showCategoryWeightsInfo());

        dataMenu.add(addGradeItem);
        dataMenu.add(viewChartItem);
        dataMenu.addSeparator();
        dataMenu.add(configureWeightsItem);
        dataMenu.addSeparator();
        dataMenu.add(saveItem);
        menuBar.add(dataMenu);

        mainFrame.setJMenuBar(menuBar);

        // --- Grade Summary Panel ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        topPanel.setBackground(UIManager.getColor("Panel.background"));

        subjectDropdown = new JComboBox<>(allGrades.keySet().toArray(new String[0]));
        subjectDropdown.setFont(new Font("SansSerif", Font.BOLD, 16));
        subjectDropdown.addItemListener(e -> updateSummary());

        JButton calculateButton = new JButton("Calculate Grade");
        calculateButton.addActionListener(e -> updateSummary());

        // Ensure labels are black
        JLabel selectLabel = new JLabel("Select Subject:");
        selectLabel.setForeground(Color.BLACK);

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        headerPanel.setBackground(UIManager.getColor("Panel.background"));
        headerPanel.add(selectLabel);
        headerPanel.add(subjectDropdown);
        headerPanel.add(calculateButton);
        topPanel.add(headerPanel, BorderLayout.NORTH);

        resultArea = new JTextArea(10, 40);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultArea.setEditable(false);

        // Explicitly set text area to light colors for reliability
        resultArea.setBackground(UIManager.getColor("TextArea.background"));
        resultArea.setForeground(Color.BLACK);

        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.getViewport().setBackground(UIManager.getColor("TextArea.background")); // Ensure viewport is also light
        topPanel.add(scrollPane, BorderLayout.CENTER);

        mainFrame.add(topPanel, BorderLayout.CENTER);

        // --- SET WINDOW SIZE HERE ---
        // We set a preferred size for the main content area (JTextArea wrapper)
        // Note: The JTextArea's constructor (10 rows, 40 columns) affects the preferred size if pack() is used.
        // We will use setSize() instead for explicit control.

        // Setting an explicit size for the main window (1000x700 pixels)
        mainFrame.setSize(1000, 700);
        // mainFrame.pack(); // REMOVED: Do not use pack() if you want a fixed size

        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);

        updateSummary(); // Initial update
    }

    /**
     * Shows a dialog with the fixed category weights.
     */
    private static void showCategoryWeightsInfo() {
        String info = String.format("Current Grading Categories:\n\nMajor: %.0f%%\nMinor: %.0f%%\n\nThese weights are fixed.",
                WEIGHT_CONFIG.get("Major"), WEIGHT_CONFIG.get("Minor"));
        JOptionPane.showMessageDialog(mainFrame, info, "Category Weights Configuration", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Loads grade data from the CSV file.
     */
    private static void loadGrades() {
        allGrades.clear();
        File file = new File(CSV_FILE);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            // Skip the header
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length == 6) {
                    try {
                        String name = parts[0].trim();
                        String subject = parts[1].trim();
                        String category = parts[2].trim();
                        int score = Integer.parseInt(parts[3].trim());
                        int outOf = Integer.parseInt(parts[4].trim());
                        String date = parts[5].trim();

                        GradeItem item = new GradeItem(name, subject, category, score, outOf, date);
                        allGrades.computeIfAbsent(subject, k -> new ArrayList<>()).add(item);
                    } catch (NumberFormatException e) {
                        System.err.println("Skipping malformed grade line: " + line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(mainFrame, "Error loading grade data: " + e.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves all grade data to the CSV file.
     */
    private static void saveGrades() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE))) {
            pw.println("Name,Subject,Category,Score,OutOf,Date");

            for (List<GradeItem> subjectGrades : allGrades.values()) {
                for (GradeItem item : subjectGrades) {
                    pw.println(item.toCSV());
                }
            }
            JOptionPane.showMessageDialog(mainFrame, "Grade data saved successfully to " + CSV_FILE, "Save Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(mainFrame, "Error saving grade data: " + e.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Shows a dialog to input a new grade item.
     */
    private static void showGradeInputDialog() {
        JDialog dialog = new JDialog(mainFrame, "Add New Grade Item", true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel formPanel = new JPanel(new GridLayout(7, 2, 5, 5));
        formPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Get list of existing subjects for the dropdown
        String[] subjects = allGrades.keySet().toArray(new String[0]);
        String[] subjectsWithNew = new String[subjects.length + 1];
        subjectsWithNew[0] = "New Subject...";
        System.arraycopy(subjects, 0, subjectsWithNew, 1, subjects.length);

        // Components
        JTextField nameField = new JTextField(15);
        JComboBox<String> subjectCombo = new JComboBox<>(subjectsWithNew);

        // Determine initial visibility: if "New Subject..." is the first item (default selected), it should be visible.
        boolean isNewSubjectDefault = "New Subject...".equals(subjectCombo.getSelectedItem());

        // --- FIX: Change initial visibility to true since "New Subject..." is the default selection ---
        JLabel subjectNewLabel = new JLabel("New Subject Name (required if 'New Subject...'):");
        subjectNewLabel.setVisible(isNewSubjectDefault); // Set initial visibility correctly

        JTextField subjectNewField = new JTextField(15);
        subjectNewField.setVisible(isNewSubjectDefault); // Set initial visibility correctly

        JComboBox<String> categoryCombo = new JComboBox<>(CATEGORIES);
        JTextField scoreField = new JTextField(15);
        JTextField outOfField = new JTextField(15);
        JTextField dateField = new JTextField(LocalDate.now().toString(), 15);

        // Logic to show/hide the new subject field AND its label
        subjectCombo.addActionListener(e -> {
            boolean isNew = "New Subject...".equals(subjectCombo.getSelectedItem());
            subjectNewField.setVisible(isNew);
            subjectNewLabel.setVisible(isNew); // Toggle the label's visibility too
            dialog.pack(); // Repack the dialog to resize the layout
        });

        // Add components to the form panel
        formPanel.add(new JLabel("Assignment Name:"));
        formPanel.add(nameField);

        formPanel.add(new JLabel("Subject:"));
        formPanel.add(subjectCombo);

        // Add the label and field (Row 3, visibility is toggled)
        formPanel.add(subjectNewLabel);
        formPanel.add(subjectNewField);

        formPanel.add(new JLabel("Weight Category:"));
        formPanel.add(categoryCombo);

        formPanel.add(new JLabel("Your Score:"));
        formPanel.add(scoreField);

        formPanel.add(new JLabel("Out Of:"));
        formPanel.add(outOfField);

        formPanel.add(new JLabel("Date (YYYY-MM-DD):"));
        formPanel.add(dateField);

        // --- Dialog Styling (Locked to Light) ---
        Color labelFg = Color.BLACK;
        Color inputBg = Color.WHITE;
        Color inputFg = Color.BLACK;
        Color panelBg = UIManager.getColor("Panel.background");

        for (Component c : formPanel.getComponents()) {
            c.setForeground(labelFg);

            // Force input component backgrounds to white with black text for standard dialog appearance
            if (c instanceof JTextField || c instanceof JComboBox) {
                c.setBackground(inputBg);
                c.setForeground(inputFg);
            }
            if (c instanceof JLabel) {
                c.setBackground(panelBg); // Ensure background consistency
            }
        }
        formPanel.setBackground(panelBg);

        // --- Save Button ---
        JButton saveButton = new JButton("Save Grade");
        saveButton.addActionListener(e -> {
            try {
                String name = nameField.getText().trim();
                String subject;

                if ("New Subject...".equals(subjectCombo.getSelectedItem())) {
                    subject = subjectNewField.getText().trim();
                } else {
                    subject = (String) subjectCombo.getSelectedItem();
                }

                String category = (String) categoryCombo.getSelectedItem();
                int score = Integer.parseInt(scoreField.getText().trim());
                int outOf = Integer.parseInt(outOfField.getText().trim());
                String date = dateField.getText().trim();

                if (name.isEmpty() || subject.isEmpty() || date.isEmpty() || outOf == 0 || category == null) {
                    JOptionPane.showMessageDialog(dialog, "Please fill all fields correctly.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (outOf <= 0) {
                    JOptionPane.showMessageDialog(dialog, "'Out Of' must be positive.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                GradeItem newItem = new GradeItem(name, subject, category, score, outOf, date);
                allGrades.computeIfAbsent(subject, k -> new ArrayList<>()).add(newItem);

                // Update the subject dropdown and the summary
                updateSubjectDropdown(subject);
                updateSummary();
                saveGrades();

                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Score and 'Out Of' must be valid integers.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(saveButton);
        buttonPanel.setBackground(panelBg);

        // Explicitly set save button text to black regardless of theme
        saveButton.setForeground(Color.BLACK);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setVisible(true);
    }

    /**
     * Updates the subject dropdown list if a new subject was added.
     */
    private static void updateSubjectDropdown(String newSubject) {
        // Clear and rebuild the dropdown to ensure correct ordering and inclusion of new subjects
        subjectDropdown.removeAllItems();

        String[] subjects = allGrades.keySet().toArray(new String[0]);
        for (String subject : subjects) {
            subjectDropdown.addItem(subject);
        }

        // Select the newly added subject (or the last calculated subject)
        if (newSubject != null) {
            subjectDropdown.setSelectedItem(newSubject);
        } else if (subjects.length > 0) {
            subjectDropdown.setSelectedIndex(0);
        }
    }

    /**
     * Recalculates and displays the summary for the currently selected subject.
     */
    private static void updateSummary() {
        String selectedSubject = (String) subjectDropdown.getSelectedItem();
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("====================================================\n"));
        sb.append(String.format("           GRADE SUMMARY FOR: %s\n", selectedSubject != null ? selectedSubject.toUpperCase() : "N/A"));
        sb.append(String.format("====================================================\n"));

        if (selectedSubject == null || !allGrades.containsKey(selectedSubject)) {
            sb.append("\nNo grades entered yet for this subject.\n");
            resultArea.setText(sb.toString());
            return;
        }

        List<GradeItem> subjectGrades = allGrades.get(selectedSubject);

        // Tracking variables for category-based calculation
        Map<String, Double> categoryScoreSums = new TreeMap<>(); // Map<Category, SumOfPercentages>
        Map<String, Double> categoryWeightCounts = new TreeMap<>(); // Map<Category, NumberOfAssignments>

        // Initialize trackers
        for (String cat : CATEGORIES) {
            categoryScoreSums.put(cat, 0.0);
            categoryWeightCounts.put(cat, 0.0);
        }

        // --- Display Individual Assignments ---
        sb.append(String.format("%-30s | %-10s | %-10s | %-10s\n",
                "Assignment Name", "Category", "Score", "Percentage"));
        sb.append(String.format("----------------------------------------------------\n"));

        for (GradeItem item : subjectGrades) {
            double percent = ((double) item.score / item.outOf); // e.g., 0.95 (decimal)

            // Tally for category average
            categoryScoreSums.put(item.category, categoryScoreSums.get(item.category) + percent);
            categoryWeightCounts.put(item.category, categoryWeightCounts.get(item.category) + 1.0);

            sb.append(String.format("%-30s | %-10s | %d/%-5d | %-10.2f\n",
                    item.name,
                    item.category,
                    item.score,
                    item.outOf,
                    percent * 100)); // Display percentage
        }

        // --- Calculate Final Grade using Category Averages ---
        double finalWeightedGrade = 0.0;
        double totalCategoryWeightUsed = 0.0;

        sb.append(String.format("====================================================\n"));
        sb.append(String.format("Category Breakdown:\n"));

        for (String cat : CATEGORIES) {
            double categoryWeight = WEIGHT_CONFIG.get(cat);
            double assignmentCount = categoryWeightCounts.get(cat);

            if (assignmentCount > 0) {
                // Calculate average percentage score for this category
                double categoryAvgPercent = categoryScoreSums.get(cat) / assignmentCount;

                // Score contribution: CategoryAvg * CategoryWeight (e.g., 0.95 * 60)
                double contribution = categoryAvgPercent * categoryWeight;
                finalWeightedGrade += contribution;
                totalCategoryWeightUsed += categoryWeight;

                sb.append(String.format("  %-10s (%.0f%%): Average %.2f%%, Contribution %.2f\n",
                        cat, categoryWeight, categoryAvgPercent * 100, contribution));
            } else {
                sb.append(String.format("  %-10s (%.0f%%): No grades yet.\n",
                        cat, categoryWeight));
            }
        }

        // Final grade scaled to 100% based on categories with assignments
        double scaledFinalGrade = 0.0;

        if (totalCategoryWeightUsed > 0) {
            scaledFinalGrade = (finalWeightedGrade / totalCategoryWeightUsed) * 100.0;
        }

        String letterGrade = computeGrade((int) Math.round(scaledFinalGrade));

        sb.append(String.format("====================================================\n"));
        sb.append(String.format("Total Category Weight Used: %.2f%%\n", totalCategoryWeightUsed));
        sb.append(String.format("Calculated Final Grade:   %.2f%%\n", scaledFinalGrade));
        sb.append(String.format("Letter Grade:             %s\n", letterGrade));
        sb.append(String.format("====================================================\n"));

        // Add a warning if not all weights are accounted for
        if (Math.abs(totalCategoryWeightUsed - 100.0) > 0.01) {
            sb.append(String.format("\nNOTE: Only %.2f%% of the total grade is currently based on assignments.\n", totalCategoryWeightUsed));
            sb.append(String.format("The 'Final Grade' above is scaled to 100%% based on categories with entered grades.\n"));
        }

        resultArea.setText(sb.toString());
    }

    // ---------------- Charting ----------------

    /**
     * Shows a line chart tracking grade progress over time.
     */
    private static void showChart() {
        String selectedSubject = (String) subjectDropdown.getSelectedItem();
        if (selectedSubject == null || !allGrades.containsKey(selectedSubject)) {
            JOptionPane.showMessageDialog(mainFrame, "No grades available to chart for this subject.", "Chart Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<GradeItem> subjectGrades = allGrades.get(selectedSubject);
        // Sort grades by date for chronological progress
        subjectGrades.sort(Comparator.comparing(item -> LocalDate.parse(item.date)));

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (int i = 0; i < subjectGrades.size(); i++) {
            GradeItem item = subjectGrades.get(i);
            // Calculate actual percentage score for the assignment
            double scorePercent = ((double) item.score / item.outOf) * 100.0;

            // Add to dataset: Value, Series (Grade), Category (Assignment Name + Date)
            dataset.addValue(scorePercent, "Assignment Score", item.name + " (" + item.date + ")");
        }

        // Create the chart
        JFreeChart chart = ChartFactory.createLineChart(
                "Grade Progress: " + selectedSubject, // Chart title
                "Assignment",                          // X-Axis label
                "Score Percentage (%)",                // Y-Axis label
                dataset,                               // Data
                PlotOrientation.VERTICAL,
                true,                                  // Include legend
                true,                                  // Tooltips
                false                                  // URLs
        );

        // Create and show the chart window
        JFrame chartFrame = new JFrame("Progress Chart: " + selectedSubject);
        chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        ChartPanel chartPanel = new ChartPanel(chart);

        // Set a preferred size for the chart panel to determine window size
        chartPanel.setPreferredSize(new Dimension(1000, 700)); // Increased chart size for better viewing

        chartFrame.setContentPane(chartPanel);

        // Apply single-theme styling to chart
        applyChartStyling(chart);

        chartFrame.pack(); // Use pack() for chart window to respect preferred size
        chartFrame.setLocationRelativeTo(mainFrame);
        chartFrame.setVisible(true);
    }

    /**
     * Helper to apply consistent Light Mode styling to chart elements.
     */
    private static void applyChartStyling(JFreeChart chart) {
        CategoryPlot plot = chart.getCategoryPlot();

        Color bg = UIManager.getColor("control"); // Light background
        Color fg = Color.BLACK; // Black text

        chart.setBackgroundPaint(bg);
        chart.getTitle().setPaint(fg);
        chart.getLegend().setBackgroundPaint(bg);
        chart.getLegend().setItemPaint(fg);

        plot.setBackgroundPaint(Color.LIGHT_GRAY); // Light gray plot background for contrast
        plot.setDomainGridlinePaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.WHITE);

        plot.getDomainAxis().setTickLabelPaint(fg);
        plot.getDomainAxis().setLabelPaint(fg);
        plot.getRangeAxis().setTickLabelPaint(fg);
        plot.getRangeAxis().setLabelPaint(fg);

        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();

        // Custom color for the line (Blue)
        renderer.setSeriesPaint(0, Color.BLUE);
        renderer.setSeriesStroke(0, new BasicStroke(3.0f));
        renderer.setSeriesShapesVisible(0, true);

        // Set fixed range for Y-axis (0-100)
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 100.0);
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        // Add 90% and 80% markers
        plot.clearRangeMarkers();
        addGradeMarker(plot, 90.0, Color.GREEN.darker(), "A-Line");
        addGradeMarker(plot, 80.0, Color.ORANGE.darker(), "B-Line");
    }

    /**
     * Helper to add a horizontal line marker to the chart plot.
     */
    private static void addGradeMarker(CategoryPlot plot, double value, Color color, String label) {
        ValueMarker marker = new ValueMarker(value);
        marker.setPaint(color);
        marker.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] {10.0f}, 0.0f));
        marker.setLabel(label);
        marker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
        marker.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
        marker.setLabelPaint(Color.BLACK); // Marker labels are black
        plot.addRangeMarker(marker);
    }

    // ---------------- Grade Calculation ----------------
    private static String computeGrade(int mark) {
        if (mark >= 97) return "A+";
        else if (mark >= 93) return "A";
        else if (mark >= 90) return "A-";
        else if (mark >= 87) return "B+";
        else if (mark >= 83) return "B";
        else if (mark >= 80) return "B-";
        else if (mark >= 77) return "C+";
        else if (mark >= 73) return "C";
        else if (mark >= 70) return "C-";
        else if (mark >= 67) return "D+";
        else if (mark >= 63) return "D";
        else if (mark >= 60) return "D-";
        else return "F";
    }
}
