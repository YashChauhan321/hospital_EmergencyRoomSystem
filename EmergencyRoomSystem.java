#include <bits/stdc++.h>
using namespace std;

/*
 Hospital Emergency Room Patient Management System
 C++ Version of the given Java code
*/

class Patient {
public:
    static long long NEXT_ARRIVAL;

    string id;
    string name;
    int age;
    int severity;
    string contact;
    long long arrivalOrder;

    static string generateID() {
        static const char alphanum[] =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        string s;
        for (int i = 0; i < 8; i++)
            s += alphanum[rand() % (sizeof(alphanum) - 1)];
        return s;
    }

    Patient(string name, int age, int severity, string contact) {
        this->id = generateID();
        this->name = trim(name);
        this->age = age;
        this->severity = severity;
        this->contact = trim(contact);
        this->arrivalOrder = NEXT_ARRIVAL++;
    }

    Patient(string id, string name, int age, int severity, string contact, long long arrival) {
        this->id = id;
        this->name = name;
        this->age = age;
        this->severity = severity;
        this->contact = contact;
        this->arrivalOrder = arrival;
        NEXT_ARRIVAL = max(NEXT_ARRIVAL, arrival + 1);
    }

    static string trim(string s) {
        while (!s.empty() && isspace(s.back())) s.pop_back();
        while (!s.empty() && isspace(s.front())) s.erase(s.begin());
        return s;
    }

    string toCsv() const {
        return id + "," + name + "," + to_string(age) + "," +
               to_string(severity) + "," + contact + "," +
               to_string(arrivalOrder);
    }

    static Patient fromCsv(string line) {
        stringstream ss(line);
        vector<string> parts;
        string token;
        while (getline(ss, token, ',')) parts.push_back(token);

        return Patient(parts[0], parts[1], stoi(parts[2]),
                       stoi(parts[3]), parts[4], stoll(parts[5]));
    }
};

long long Patient::NEXT_ARRIVAL = 0;


/* ---------- Comparator for Max Heap ---------- */
struct Compare {
    bool operator()(const Patient &a, const Patient &b) const {
        if (a.severity != b.severity)
            return a.severity < b.severity; // max heap
        return a.arrivalOrder > b.arrivalOrder;
    }
};


/* ---------- System ---------- */

class EmergencyRoomSystem {

    priority_queue<Patient, vector<Patient>, Compare> heap;
    vector<Patient> nameSorted;
    list<Patient> treatedHistory;

    string waitingFile = "patients_waiting.csv";
    string treatedFile = "patients_treated.csv";

public:

    /* ---------- Binary Search Helpers ---------- */

    int lowerBoundByName(string key) {
        int lo = 0, hi = nameSorted.size();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (strcasecmp(nameSorted[mid].name.c_str(), key.c_str()) < 0)
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }

    void insertIntoNameSorted(const Patient &p) {
        int pos = lowerBoundByName(p.name);
        nameSorted.insert(nameSorted.begin() + pos, p);
    }

    void removeFromNameSorted(const Patient &p) {
        int idx = lowerBoundByName(p.name);
        for (int i = idx; i < nameSorted.size(); i++) {
            if (nameSorted[i].name != p.name) break;
            if (nameSorted[i].id == p.id) {
                nameSorted.erase(nameSorted.begin() + i);
                return;
            }
        }
    }

    /* ---------- Core Functions ---------- */

    void addPatient(string name, int age, int severity, string contact) {
        if (severity < 1 || severity > 10) {
            cout << "Severity must be 1-10\n";
            return;
        }
        Patient p(name, age, severity, contact);
        heap.push(p);
        insertIntoNameSorted(p);
        cout << "Added: " << p.name << " Sev=" << p.severity << endl;
    }

    void treatNext() {
        if (heap.empty()) {
            cout << "No patients waiting\n";
            return;
        }
        Patient p = heap.top();
        heap.pop();

        removeFromNameSorted(p);
        treatedHistory.push_front(p);

        cout << "Treating: " << p.name << " Sev=" << p.severity << endl;
    }

    void viewAll() {
        if (heap.empty()) {
            cout << "No patients waiting\n";
            return;
        }

        vector<Patient> temp;
        auto copy = heap;
        while (!copy.empty()) {
            temp.push_back(copy.top());
            copy.pop();
        }

        cout << "\n-- Waiting Patients --\n";
        for (int i = 0; i < temp.size(); i++)
            cout << i+1 << ") " << temp[i].name
                 << " Age=" << temp[i].age
                 << " Sev=" << temp[i].severity << endl;
    }

    void searchByName(string key) {
        int idx = lowerBoundByName(key);
        bool found = false;

        while (idx < nameSorted.size() && nameSorted[idx].name == key) {
            auto &p = nameSorted[idx];
            cout << "- " << p.name << " Age=" << p.age
                 << " Sev=" << p.severity << endl;
            idx++;
            found = true;
        }

        if (!found)
            cout << "No match found\n";
    }

    /* ---------- Persistence ---------- */

    void saveState() {
        ofstream w(waitingFile);
        auto copy = heap;
        while (!copy.empty()) {
            w << copy.top().toCsv() << "\n";
            copy.pop();
        }
        w.close();

        ofstream t(treatedFile);
        for (auto &p : treatedHistory)
            t << p.toCsv() << "\n";
        t.close();

        cout << "State saved.\n";
    }

    void loadState() {
        ifstream w(waitingFile);
        string line;
        while (getline(w, line)) {
            if (line.empty()) continue;
            Patient p = Patient::fromCsv(line);
            heap.push(p);
            insertIntoNameSorted(p);
        }
        w.close();

        ifstream t(treatedFile);
        while (getline(t, line)) {
            if (line.empty()) continue;
            treatedHistory.push_back(Patient::fromCsv(line));
        }
        t.close();
    }

    /* ---------- CLI ---------- */

    void run() {
        loadState();

        while (true) {
            cout << "\n====== ER Patient Management ======\n";
            cout << "1) Add Patient\n2) Treat Next\n3) View All\n4) Search\n5) Exit\nChoose: ";

            string choice;
            getline(cin, choice);

            if (choice == "1") {
                string name, contact;
                int age, sev;

                cout << "Name: "; getline(cin, name);
                cout << "Age: "; cin >> age;
                cout << "Severity: "; cin >> sev;
                cin.ignore();
                cout << "Contact: "; getline(cin, contact);

                addPatient(name, age, sev, contact);
            }
            else if (choice == "2") treatNext();
            else if (choice == "3") viewAll();
            else if (choice == "4") {
                string name;
                cout << "Enter name: ";
                getline(cin, name);
                searchByName(name);
            }
            else if (choice == "5") {
                saveState();
                return;
            }
        }
    }
};


int main() {
    srand(time(0));
    EmergencyRoomSystem().run();
}
