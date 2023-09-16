package org.example;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class Solution {

  static final ConcurrentSkipListSet<Long> threadsBucket = new ConcurrentSkipListSet<>();

  static BigInteger[][] degrees = new BigInteger[10][19];

  public static long[] getNumbers(long N) throws InterruptedException {
    if (N < 0) return new long[0];
    if (N > 10 && threadsBucket.size() == 0) {
      LongStream.rangeClosed(1, 9).forEach(threadsBucket::add);
    } else if (N < 10 && threadsBucket.size() == 0) {
      LongStream.rangeClosed(1, N).forEach(threadsBucket::add);
      return threadsBucket.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    //Optimizing task volume (get max number from N with ascending digits order)
    List<Integer> taskLimitDigits = getDigits(N);
    int zeroCount = (int) taskLimitDigits.stream().filter(d -> d == 0).count();
    taskLimitDigits = taskLimitDigits.stream().sorted().collect(Collectors.toList());
    Long taskLimit = getNumberFromDigits(taskLimitDigits);
    for (int i = 0; i < zeroCount; i++) {
      taskLimit *= 10;
      taskLimit += 9;
    }

    taskLimit = taskLimit - taskLimit / 3;

    degrees = fillCommonDegrees(taskLimitDigits.size() + 4);  //+4 for extra zeros check

    int availableCores = Runtime.getRuntime().availableProcessors() - 1;
    //availableCores = 1;
    ExecutorService executor = Executors.newFixedThreadPool(availableCores);

    long taskVolume = 0;
    long additionalTaskVolume = 0;
    long taskRangeIndex = 0;

    //Calculating task volume for each thread
    if (taskLimit % availableCores == 0) {
      taskVolume = taskLimit / availableCores;
    } else {
      taskVolume = (taskLimit - 1) / availableCores;
      additionalTaskVolume = taskLimit - taskVolume * availableCores;
    }

    //Starting threads with divided tasks
    for (int i = 0; i < availableCores; i++) {
      if (i == availableCores - 1) taskVolume += additionalTaskVolume;
      taskRangeIndex += taskVolume;
      ArmstrongCalculator armstrongCalculator = new ArmstrongCalculator(taskVolume * i, taskRangeIndex, N);
      executor.execute(armstrongCalculator);
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    System.out.println("Calculated on " + availableCores + " threads");
    System.out.println("Task volume: " + N);
    return threadsBucket.stream().mapToLong(Long::longValue).sorted().toArray();
  }


  public static void main(String[] args) throws InterruptedException {
    long a = System.currentTimeMillis();
    System.out.println(Arrays.toString(getNumbers(Long.MAX_VALUE)));
    long b = System.currentTimeMillis();
    System.out.println("memory " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (8 * 1024) + " kb");
    System.out.println("time = " + (b - a) + " ms");
  }

  private static List<Integer> getDigits(long number) {
    List<Integer> result = new ArrayList<>();
    String numberStr = String.valueOf(number);
    for (int i = 0; i < numberStr.length(); i++) {
      result.add(Character.digit(numberStr.charAt(i), 10));
    }
    return result; //Long.toString(number).chars().map(ch -> Character.digit(ch, 10)).boxed().collect(Collectors.toList());
  }

  private static void generateNumbers(long start, long end, long limit) {
    List<Integer> digits;
    //370 is first Armstrong number except 1..9 range and 153 (will be found later)
    if (start < 37) start = 37; //1123444568L;

    //Searching numbers in range
    for (long i = start; i < end; i++) {
      if (i < 0) break; //breaking cycle in case long overflow
      digits = getDigits(i);
      int numberSize = digits.size();
      if (numberSize < 3) {
        digits.add(0, 0);
        numberSize++;
      }

      boolean ascMarker = false;

      //Checking ascending order of digits in number
      for (int j = 1; j < numberSize - 1; j++) {
        ascMarker = digits.get(j - 1) <= digits.get(j) && digits.get(j) <= digits.get(j + 1);
        if (!ascMarker) {
          int currDigit = digits.get(j);
          int digitPointer = j;

          if (currDigit > digits.get(j + 1)) digitPointer++;
          else if (currDigit < digits.get(j - 1)) {
            while (currDigit != digits.get(j - 1)) currDigit++;
          }

          //switching pointer to next potentially good number
          for (int g = digitPointer; g <= digits.size() - 1; g++) {
            digits.set(g, currDigit);
          }
          i = getNumberFromDigits(digits) - 1;
          break;
        }
      }

      long powSum;
      //Calculate digit pow sum only for numbers with ascending digits order
      if (ascMarker) {
        powSum = calculatePowSum(digits).longValue();
        if (powSum < limit && checkNumber(powSum, digits)) {
          threadsBucket.add(powSum);
          System.out.println("Found: " + powSum + " by " + Thread.currentThread().getName() + ", is it in range: " + (powSum > start && powSum < end));
        } else {
          for (int k = 0; k < 3; k++) {
            digits.add(0, 0);
            powSum = calculatePowSum(digits).longValue();
            if (powSum < limit && checkNumber(powSum, digits) && powSum != 4150) {
              threadsBucket.add(powSum);
              System.out.println("Found: " + powSum + " by " + Thread.currentThread().getName() + ", is it in range: " + (powSum > start && powSum < end));
              break;
            }
          }
        }
      }
    }
  }

  private static BigInteger[][] fillCommonDegrees(int N) {
    BigInteger[][] result = new BigInteger[10][N];
    for (int i = 0; i < N; i++) {
      result[0][i] = BigInteger.ZERO;
    }
    for (int i = 1; i < 10; i++) {
      result[i][0] = BigInteger.ZERO;
    }
    for (int i = 1; i < N; i++) {
      result[1][i] = BigInteger.ONE;
    }
    for (int i = 2; i < 10; i++) {
      for (int j = 1; j < result[0].length; j++) {
        result[i][j] = BigInteger.valueOf(i).pow(j);
      }
    }
    return result;
  }

  private static BigInteger calculatePowSum(List<Integer> digits) {
    BigInteger[] calc = new BigInteger[1];
    calc[0] = BigInteger.ZERO;
    for (Integer digit : digits) {
      calc[0] = calc[0].add(degrees[digit][digits.size()]);
    }
    //digits.forEach(d -> calc[0] += degrees[d][digits.size()]);
    if (calc[0].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return BigInteger.valueOf(Long.MAX_VALUE);
    else return calc[0];
  }

  //Check q-ty of each digit appearance in pow sum and original number
  private static boolean checkNumber(long number, List<Integer> initialNumber) {
    int[] digits1 = new int[10];
    int[] digits2 = new int[10];
    initialNumber.forEach(d -> digits1[d]++);
    List<Integer> separateDigits = getDigits(number);
    separateDigits.forEach(d -> {
      digits2[d]++;
    });
    boolean okMarker = Arrays.equals(digits1, digits2);
    return okMarker;
  }

  private static Long getNumberFromDigits(List<Integer> digits) {
    long[] number = new long[1];
    digits.forEach(d -> number[0] = number[0] * 10 + d);
    return number[0];
  }

  private static class ArmstrongCalculator implements Runnable {
    private final long start;
    private final long end;

    private final long limit;

    private ArmstrongCalculator(long start, long end, long limit) {
      this.start = start;
      this.end = end;
      this.limit = limit;
    }

    @Override
    public void run() {
      generateNumbers(start, end, limit);
    }
  }
}
