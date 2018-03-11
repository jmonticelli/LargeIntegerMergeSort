# LargeIntegerMergeSort
An external integer merge sort that can sort files of integers which cannot fit into RAM.

This program expects a newline-delimited file which contains plain-text integers (-2147483648 to 2147483647).

It outputs a sorted file in the same format, and during the creation of the program partitions the file into as many binary integer split files as required for the program to sort in a given amount of RAM.

It should be noted that to make this program work, you can't make your sort size infinitely small, as there needs to be enough room in the buffer to pull integers from the file.

Obviously, there's a sweet spot for making the sort the most efficient. Too many partitions can incur incredibly large I/O overhead, and a partition the size of the input file should have been sorted internally anyway and wastes I/O.

This is more of a challenge I did to practice implementing the concept of external merge sorting.
