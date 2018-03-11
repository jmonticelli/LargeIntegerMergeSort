/*
 * This program, if distributed by its author to the public as source code,
 * can be used if credit is given to its author and any project or program
 * released with the source code is released under the same stipulations.
 */
package largedatamergesort;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;


// This program assumes a LARGE input file of unsorted integers
// as STRING data, seperated by newlines

// Then, the file will be broken up into 500MB binary integer files
// and sort them in memory using merge sort. We will also keep track of
// the files we create, so we can merge them together and finally output a
// large sorted file.



/**
 *
 * @author Julian
 */
public class LargeDataMergeSort {

    public static int MAX_RAM = 0;
    public static int MAX_SORT_RAM = 0;
    
    public static String WORKSPACE = null;
    public static String INPUT_FILE = null;
    public static String INPUT_FILE_LOCATION = null;
    
    
    public static void main(String[] args) {
        System.out.println(args.length);
        if (args.length != 2) {
            System.out.println("This program requires two arguments: a file with"
                    + " decimal numbers to sort, and a maximum amount of "
                    + "allotted RAM for the array of integers.");
            System.out.println("The program should be run as follows:");
            System.out.println("LargeDataMergeSort <filename> <ramSize>");
            System.out.println("Where the ramSize is an integer or String parsed"
                    + " as \"1G\" or \"256M\".");
            return;
        }
        else {
            File f = new File(args[0]);
            INPUT_FILE = f.getName();
            WORKSPACE = f.getPath().substring(0, 
                    (f.getPath().length()-INPUT_FILE.length()));
            INPUT_FILE_LOCATION = WORKSPACE + INPUT_FILE;
            
            String mem = args[1];
            
            // YES - I'm simplifying everything and using metric prefixes,
            // not the 2^10 interpretation of K, M, and G
            mem = mem.toUpperCase();
            mem = mem.replaceFirst("K", "000");
            mem = mem.replaceFirst("M", "000000");
            mem = mem.replaceFirst("G", "000000000");
            try {
                MAX_RAM = Integer.parseInt(mem);
                MAX_SORT_RAM = MAX_RAM/2;
                if (MAX_SORT_RAM < 500) {
                    System.out.println("The program should have at LEAST 1K "
                            + "memory allotted to it in order to be expected "
                            + "to work.\n\nThe larger the file, however, the "
                            + "larger the lower limit of the memory.");
                    System.exit(-255);
                }
                if (f.length() > MAX_RAM * 1200) {
                    System.out.println("The program requires a reasonable "
                            + "fraction of memory allotted to it in order to be"
                            + "\nexpected to work.\n\nA crude approximation "
                            + "is that a sorted file of ASCII integers should be "
                            + "at most 1200 times\nlarger than its allotted sort "
                            + "size in RAM.");
                    System.out.println("MAX_RAM * 1200: " + MAX_RAM * 1200);
                    System.out.println("FILE SIZE: " + f.length());
                    System.exit(-255);
                }
            } catch (NumberFormatException e) {
                System.out.println("The requested amount of memory is not in"
                        + " a supported format! It is key to note that only"
                        + " integers OR integers followed by ONE of the"
                        + " following characters is permitted: K, M, G");
            }
        }
        
        int maxInts = MAX_SORT_RAM / 4; // Size of int
        
        
        int numFiles = createSortPartitions(maxInts);
        sortPartitionedFiles(numFiles);
        deletePartitionedFiles(numFiles);
    }
    
    public static int createSortPartitions(int maxInts) {
        int numFiles = 0;// 0;        
        
        try (Scanner sc = new Scanner(new File(INPUT_FILE_LOCATION)))
        {
            int intCounter = 0;
            int currentFile = 0;
            DataOutputStream dos = null;
            
            int[] intArray = null;
            
            while (sc.hasNextLine()) {
                // Create new empty array if we need to
                if (intCounter == 0) {
                    intArray = new int[maxInts];
                }
                
                
                int nextInt = sc.nextInt();
                sc.nextLine(); // Consume newline
                intArray[intCounter] = nextInt;
                intCounter = (intCounter + 1) % maxInts;
                
                // Handles sorting, file writing, and creating new files
                if (intCounter == 0 || !sc.hasNextLine()) {
                    
                    // Create new DataOutputStream
                    numFiles++;
                    currentFile++;
                    try {
                        dos = new DataOutputStream(
                                new BufferedOutputStream(
                                new FileOutputStream(WORKSPACE+"S"+currentFile)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    
                    
                    // Handle sorting, writing to the file, and closing it
                    
                    // Determine whet our end index is
                    int endIndex = intCounter > 0 ? intCounter-1 : intArray.length-1;
                    
                    // Sort with our preferred sort method (mergesort)
                    sort(intArray, endIndex);
                    
                    try {
                        for (int i = 0; i < endIndex; i++) {
                            dos.writeInt(intArray[i]);
                        }
                        dos.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            System.out.println("The input file was not found. Exiting program.");
            System.out.println("Offending file path is: " + INPUT_FILE_LOCATION);
            System.exit(-1);
        }
        return numFiles;
    }
    
    public static void sortPartitionedFiles(int numFiles) {
        // Create a considerable buffer size for each file
        // and leave double the buffer size for the output
        // file.
        int bufferSize = MAX_RAM / (numFiles + 2);
        
        if (bufferSize < 4) {
            System.out.println("An error occurred when going to sort. The "
                    + "allotted amount of memory which we were given\n to sort "
                    + "is too small to give us a reasonable buffer size to work "
                    + "with.\n\n"
                    + "The program will now exit and clean up all files it "
                    + "created.\n\nSorry for the extra disk usage :-)");
            deletePartitionedFiles(numFiles);
            System.exit(-255);
        }
        
        DataInputStream[] dis = new DataInputStream[numFiles];
        boolean[] streamOpen = new boolean[numFiles];
        boolean[] hasValue = new boolean[numFiles];
        int[] minBuffer = new int[numFiles];
        
        for (int i = 1; i <= numFiles; i++) {
            try {
                dis[i-1] = new DataInputStream(
                        new BufferedInputStream(
                                new FileInputStream(
                                        WORKSPACE+"S"+i)
                                , bufferSize)
                );
                streamOpen[i-1] = true;
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
                System.exit(-1);
            }
        }
        
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(
                    new BufferedWriter(
                            new FileWriter(WORKSPACE+"sorted.txt"), bufferSize*2));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        
        boolean intsLeft = true;
        while (intsLeft) {
            
            intsLeft = false;
            
            int minValue = Integer.MAX_VALUE;
            int minIndex = -1;
            
            for (int i = 0; i < numFiles; i++) {
                if (streamOpen[i]) {
                    intsLeft = true;
                    if (!hasValue[i]) {
                        try {
                            minBuffer[i] = dis[i].readInt();
                            hasValue[i] = true;
                        } catch (EOFException eof) {
                            try {
                                System.out.println("Stream " + i + " closed");
                                dis[i].close();
                                streamOpen[i] = false;
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }
                    if (minValue >= minBuffer[i]) {
                        minValue = minBuffer[i];
                        minIndex = i;
                    }
                }
            }
            
            if (minIndex > -1) {
                pw.println(minValue);
                hasValue[minIndex] = false;
            }
        }
        
        pw.close();
        System.out.println("Sort successful!");
        
    }
    
    
    public static void deletePartitionedFiles(int numFiles) {
        for(int i = 1; i <= numFiles; i++) {
            File f = new File(WORKSPACE+"S"+i);
            if (!f.delete()) {
                System.out.println("Failed to delete data partition file S" + i);
            }
        }
    }
    
    
    public static void sort(int[] arr, int endIndex) {
        mergeSort(arr, 0, endIndex);
    }
    
    public static void merge(int[] arr, int low, int mid, int high) {
        
        int n1 = mid - low + 1;
        int n2 = high - mid;
        
        int[] leftArr = new int[n1];
        int[] rightArr = new int[n2];
        
        for (int i = 0; i < n1; i++) {
            leftArr[i] = arr[low + i];
        }
        for (int i = 0; i < n2; i++) {
            rightArr[i] = arr[i + mid + 1];
        }
        
        int l = 0;
        int r = 0;
        int k = low;
        while (l < n1 && r < n2) {
            if (leftArr[l] < rightArr[r]) {
                arr[k] = leftArr[l];
                l++;
                k++;
            }
            else {
                arr[k] = rightArr[r];
                r++;
                k++;
            }
        }
        
        while (l < n1) {
            arr[k] = leftArr[l];
            l++;
            k++;
        }
        
        while (r < n2) {
            arr[k] = rightArr[r];
            r++;
            k++;
        }
        
    }
    
    public static void mergeSort(int[] arr, int low, int high) {
        if (low < high) {
            int mid = (low+(high-1)) / 2;
            
            mergeSort(arr, low, mid);
            mergeSort(arr, mid+1, high);
            merge(arr, low, mid, high);
        }
    }
    
}
