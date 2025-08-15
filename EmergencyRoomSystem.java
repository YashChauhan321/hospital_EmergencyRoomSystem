import java.io.*;
import java.util.*;

/**
 * Hospital Emergency Room Patient Management System
 * -----------------------------------------------
 * DSA used:
 *  - Priority Queue (Heap) to prioritize patients by severity (max first)
 *  - LinkedList to keep treatment history
 *  - Binary Search over a name-sorted ArrayList for searching by name
 *
 * Features:
 *  - Add patient (Name, Age, Severity 1-10, Contact)
 *  - Treat next patient (removes highest-severity, FIFO on ties)
 *  - View all waiting patients ordered by severity
 *  - Search patient(s) by exact name (binary search on a name-sorted list)
 *  - Persist waiting queue and treated history to CSV on exit; auto-load on start
 */
public class EmergencyRoomSystem {

    // ---------- Patient Model ----------
    private static class Patient {
        private static long NEXT_ARRIVAL = 0; // increasing tie-breaker
        final UUID id;            // unique identifier for persistence and debugging
        final String name;
        final int age;
        final int severity;      // 1..10 (10 = most severe)
        final String contact;
        final long arrivalOrder; // smaller means earlier arrival

        Patient(String name, int age, int severity, String contact) {
            this(UUID.randomUUID(), name, age, severity, contact, NEXT_ARRIVAL++);
        }

        Patient(UUID id, String name, int age, int severity, String contact, long arrivalOrder) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.severity = severity;
            this.contact = contact;
            this.arrivalOrder = arrivalOrder;
            // ensure NEXT_ARRIVAL always moves forward even when loading
            if (arrivalOrder >= NEXT_ARRIVAL) {
                NEXT_ARRIVAL = arrivalOrder + 1;
            }
        }

        // CSV serialization (very simple, escapes commas by replacing with spaces)
        String toCsv() {
            return String.join(",",
                    id.toString(),
                    esc(name),
                    Integer.toString(age),
                    Integer.toString(severity),
                    esc(contact),
                    Long.toString(arrivalOrder)
            );
        }

        static String esc(String s) { return s.replace(",", " ").trim(); }

        static Patient fromCsv(String line) {
            String[] parts = line.split(",");
            if (parts.length < 6) throw new IllegalArgumentException("Bad CSV line: " + line);
            UUID id = UUID.fromString(parts[0].trim());
            String name = parts[1].trim();
            int age = Integer.parseInt(parts[2].trim());
            int severity = Integer.parseInt(parts[3].trim());
            String contact = parts[4].trim();
            long arrival = Long.parseLong(parts[5].trim());
            return new Patient(id, name, age, severity, contact, arrival);
        }

        @Override public String toString() {
            return String.format("[%s] name=%s | age=%d | severity=%d | contact=%s | arrival=%d",
                    id.toString().substring(0, 8), name, age, severity, contact, arrivalOrder);
        }
    }

    // ---------- Data Structures ----------

    // Max-heap by severity, then earlier arrival first
    private final PriorityQueue<Patient> heap = new PriorityQueue<>(
            (a, b) -> {
                if (b.severity != a.severity) return b.severity - a.severity; // higher severity first
                return Long.compare(a.arrivalOrder, b.arrivalOrder);          // earlier arrival first
            }
    );

    // Name-sorted list for binary search (stable by name, ties by arrival)
    private final ArrayList<Patient> nameSorted = new ArrayList<>();

    // Treatment history (linked list)
    private final LinkedList<Patient> treatedHistory = new LinkedList<>();

    // Files for persistence
    private final File waitingFile = new File("patients_waiting.csv");
    private final File treatedFile = new File("patients_treated.csv");

    // ---------- API ----------

    public void addPatient(String name, int age, int severity, String contact) {
        if (severity < 1 || severity > 10) {
            System.out.println("Severity must be 1-10. Try again.");
            return;
        }
        Patient p = new Patient(name.trim(), age, severity, contact.trim());
        heap.add(p);
        insertIntoNameSorted(p);
        System.out.println("Added: " + p);
    }

    public void treatNext() {
        Patient p = heap.poll();
        if (p == null) {
            System.out.println("No patients waiting.");
            return;
        }
        // remove from nameSorted as well
        removeFromNameSorted(p);
        treatedHistory.addFirst(p); // most recent at head
        System.out.println("Treating: " + p);
    }

    public void viewAll() {
        if (heap.isEmpty()) {
            System.out.println("No patients waiting.");
            return;
        }
        // Non-destructively list by severity desc, arrival asc
        List<Patient> snapshot = new ArrayList<>(heap);
        snapshot.sort((a, b) -> {
            if (b.severity != a.severity) return b.severity - a.severity;
            return Long.compare(a.arrivalOrder, b.arrivalOrder);
        });
        System.out.println("\n-- Waiting Patients (by severity) --");
        int i = 1;
        for (Patient p : snapshot) {
            System.out.printf("%2d) %s | Age=%d | Sev=%d | Contact=%s\n",
                    i++, p.name, p.age, p.severity, p.contact);
        }
        System.out.println();
    }

    public void searchByName(String exactName) {
        if (nameSorted.isEmpty()) {
            System.out.println("No patients waiting.");
            return;
        }
        String key = exactName.trim();
        int idx = lowerBoundByName(key);
        if (idx < 0 || idx >= nameSorted.size()) {
            System.out.println("No match found for name: " + key);
            return;
        }
        List<Patient> matches = new ArrayList<>();
        for (int i = idx; i < nameSorted.size(); i++) {
            Patient p = nameSorted.get(i);
            if (!p.name.equalsIgnoreCase(key)) break;
            matches.add(p);
        }
        if (matches.isEmpty()) {
            System.out.println("No match found for name: " + key);
        } else {
            // Display matches in severity order for usefulness
            matches.sort((a, b) -> {
                if (b.severity != a.severity) return b.severity - a.severity;
                return Long.compare(a.arrivalOrder, b.arrivalOrder);
            });
            System.out.println("Matches for \"" + key + "\" (waiting only):");
            for (Patient p : matches) {
                System.out.printf("- %s | Age=%d | Sev=%d | Contact=%s | id=%s\n",
                        p.name, p.age, p.severity, p.contact, p.id.toString().substring(0, 8));
            }
        }
    }

    // ---------- Name-sorted helpers (Binary Search) ----------

    private void insertIntoNameSorted(Patient p) {
        int lo = 0, hi = nameSorted.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            Patient m = nameSorted.get(mid);
            int cmp = p.name.compareToIgnoreCase(m.name);
            if (cmp > 0 || (cmp == 0 && p.arrivalOrder > m.arrivalOrder)) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        nameSorted.add(lo, p);
    }

    private void removeFromNameSorted(Patient p) {
        int idx = lowerBoundByName(p.name);
        if (idx < 0) return;
        // walk forward to exact object match (by id)
        for (int i = idx; i < nameSorted.size(); i++) {
            Patient q = nameSorted.get(i);
            if (!q.name.equalsIgnoreCase(p.name)) break;
            if (q.id.equals(p.id)) {
                nameSorted.remove(i);
                return;
            }
        }
    }

    // first index whose name >= key (case-insensitive)
    private int lowerBoundByName(String key) {
        int lo = 0, hi = nameSorted.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            Patient m = nameSorted.get(mid);
            int cmp = m.name.compareToIgnoreCase(key);
            if (cmp < 0) lo = mid + 1; else hi = mid;
        }
        if (lo >= nameSorted.size()) return lo; // may be out of range (caller checks)
        return lo;
    }

    // ---------- Persistence ----------

    private void saveState() {
        try {
            // waiting queue
            try (PrintWriter out = new PrintWriter(new FileWriter(waitingFile))) {
                for (Patient p : heap) {
                    out.println(p.toCsv());
                }
            }
            // treated history (most recent first)
            try (PrintWriter out = new PrintWriter(new FileWriter(treatedFile))) {
                for (Patient p : treatedHistory) {
                    out.println(p.toCsv());
                }
            }
            System.out.println("State saved to: " + waitingFile.getName() + ", " + treatedFile.getName());
        } catch (IOException e) {
            System.err.println("Failed to save state: " + e.getMessage());
        }
    }

    private void loadState() {
        int loadedWaiting = 0, loadedTreated = 0;
        if (waitingFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(waitingFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Patient p = Patient.fromCsv(line);
                    heap.add(p);
                    insertIntoNameSorted(p);
                    loadedWaiting++;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("Failed to load waiting file: " + e.getMessage());
            }
        }
        if (treatedFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(treatedFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Patient p = Patient.fromCsv(line);
                    treatedHistory.add(p);
                    loadedTreated++;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("Failed to load treated file: " + e.getMessage());
            }
        }
        if (loadedWaiting > 0 || loadedTreated > 0) {
            System.out.printf("Loaded %d waiting, %d treated from previous session.%n", loadedWaiting, loadedTreated);
        }
    }

    // ---------- CLI ----------

    private void runCli() {
        loadState();
        Scanner sc = new Scanner(System.in);
        while (true) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                    try {
                        System.out.print("Name: ");
                        String name = sc.nextLine();
                        System.out.print("Age: ");
                        int age = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Severity (1-10, 10=highest): ");
                        int severity = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Contact: ");
                        String contact = sc.nextLine();
                        addPatient(name, age, severity, contact);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid number. Operation cancelled.");
                    }
                    break;
                case "2":
                    treatNext();
                    break;
                case "3":
                    viewAll();
                    break;
                case "4":
                    System.out.print("Enter exact name to search: ");
                    String name = sc.nextLine();
                    searchByName(name);
                    break;
                case "5":
                    saveState();
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Unknown option. Try again.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n====== ER Patient Management ======");
        System.out.println("1) Add Patient");
        System.out.println("2) Treat Next Patient");
        System.out.println("3) View All Waiting Patients");
        System.out.println("4) Search Patient by Name");
        System.out.println("5) Exit (save)");
        System.out.print("Choose: ");
    }

    public static void main(String[] args) {
        new EmergencyRoomSystem().runCli();
    }
}
