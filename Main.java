import java.util.ArrayList;
import java.util.Scanner;

class Student {
    String name;
    int grade;

    Student(String name, int grade) {
        this.name = name;
        this.grade = grade;
    }
}

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ArrayList<Student> students = new ArrayList<>();

        System.out.print("Enter number of students: ");
        int numStudents = scanner.nextInt();
        scanner.nextLine(); 

        for (int i = 0; i < numStudents; i++) {
            System.out.print("Enter name of student #" + (i + 1) + ": ");
            String name = scanner.nextLine();

            System.out.print("Enter grade for " + name + ": ");
            int grade = scanner.nextInt();
            scanner.nextLine(); 

            students.add(new Student(name, grade));
        }
        int total = 0, highest = Integer.MIN_VALUE, lowest = Integer.MAX_VALUE;
        for (Student s : students) {
            total += s.grade;
            if (s.grade > highest) highest = s.grade;
            if (s.grade < lowest) lowest = s.grade;
        }
        double average = (double) total / students.size();

        System.out.println("\n--- Student Summary Report ---");
        for (Student s : students) {
            System.out.println("Name: " + s.name + ", Grade: " + s.grade);
        }

        System.out.printf("\nAverage Grade: %.2f\n", average);
        System.out.println("Highest Grade: " + highest);
        System.out.println("Lowest Grade: " + lowest);

        scanner.close();
    }
}