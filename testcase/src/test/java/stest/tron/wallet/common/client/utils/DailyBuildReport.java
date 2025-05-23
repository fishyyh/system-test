package stest.tron.wallet.common.client.utils;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.tron.api.GrpcAPI;
import org.tron.api.WalletGrpc;
import org.tron.protos.Protocol;
import stest.tron.wallet.common.client.Configuration;
@Slf4j
public class DailyBuildReport extends TestListenerAdapter {

  StringBuffer passedDescriptionList = new StringBuffer("");
  StringBuffer failedDescriptionList = new StringBuffer("");
  StringBuffer skippedDescriptionList = new StringBuffer("");
  private AtomicInteger passedNum = new AtomicInteger(0);
  private AtomicInteger failedNum = new AtomicInteger(0);
  private AtomicInteger skippedNum = new AtomicInteger(0);
  private String reportPath;
  public Map<String, Integer> transactionType = new HashMap<>();
  public  Long endBlockNum = 0L;
  public static Long startBlockNum = 0L;
  public AtomicLong totalTransactionNum = new AtomicLong(0L);
  public ManagedChannel channelFull = null;
  public WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
          .getStringList("fullnode.ip.list").get(0);

  private String reportFlag = Configuration.getByPath("testng.conf")
          .getString("defaultParameter.ReportFlag");

  private String slack = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.slack");

  @Override
  public void onConfigurationFailure(ITestResult itr) {
    super.onConfigurationFailure(itr);
    failedDescriptionList.append(itr.getTestClass().getName() + ": "
        + "beforeClass" + "\n");
    failedNum.getAndAdd(1);
    String caseFailedNotification = itr.getTestClass().getName() + " beforeClass Failed";
    logger.info(caseFailedNotification);
    String cmd = slack + " " + caseFailedNotification;
    try {
      logger.info("send slack begin className: " + itr.getTestClass().getName());
      PublicMethed.exec(cmd);
      logger.info("send slack end className: " + itr.getTestClass().getName());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }


  @Override
  public void onStart(ITestContext context) {
    reportPath = "Daily_Build_Report";
    StringBuilder sb = new StringBuilder("3.Stest report:  ");
    String res = sb.toString();
    try {
      Files.write((Paths.get(reportPath)), res.getBytes("utf-8"));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onTestStart(ITestResult result) {
    logger.info(result.getMethod().getRealClass().getName() + "." + result.getMethod().getMethodName() + " Start");
  }


  @Override
  public void onTestSuccess(ITestResult result) {
    passedDescriptionList.append(result.getMethod().getRealClass() + ": "
        + result.getMethod().getDescription() + "\n");
    passedNum.getAndAdd(1);
    logger.info(result.getMethod().getRealClass().getName() + "." + result.getMethod().getMethodName() + " Success");
  }

  @Override
  public void onTestFailure(ITestResult result) {
    failedDescriptionList.append(result.getMethod().getRealClass() + ": "
        + result.getMethod().getDescription() + "\n");
    failedNum.getAndAdd(1);
    String caseFailedNotification = result.getMethod().getRealClass().getName() + "." + result.getMethod().getMethodName() + " Failed";
    logger.info(caseFailedNotification);
    String cmd = slack + " " + caseFailedNotification;
    try {
      logger.info("send slack begin caseName: "+ result.getMethod().getMethodName());
      PublicMethed.exec(cmd);
      logger.info("send slack end caseName: "+ result.getMethod().getMethodName());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void onTestSkipped(ITestResult result) {
    skippedDescriptionList.append(result.getMethod().getRealClass() + ": "
        + result.getMethod().getDescription() + "\n");
    skippedNum.getAndAdd(1);
    logger.info(result.getMethod().getRealClass().getName() + "." + result.getMethod().getMethodName() + " Skip");

  }


  @Override
  public void onFinish(ITestContext testContext) {
    if(testContext.getName().equals(reportFlag)) {
      logger.info(testContext.getName() + "test finished, start to generate report...");
      StringBuilder sb = new StringBuilder();
      // on beforeClass Failed occur, Total num will be bigger than ALL-pass case.
      sb.append("Total: " + (passedNum.get() + failedNum.get() + skippedNum.get()) + ",  " + "Passed: " + passedNum
          + ",  " + "Failed: " + failedNum + ",  " + "Skipped: " + skippedNum + "\n");
      sb.append("------------------------------------------------------------------------------\n");
      List<Map.Entry<String, Integer>> list = calculateAfterDailyBuild();
      sb.append("Total transaction number:" + totalTransactionNum.get() + "\n");
      sb.append("Transaction type list:" + "\n");
      for (Map.Entry<String, Integer> entry : list) {
        sb.append(entry.getKey());
        for (int i = entry.getKey().length(); i < 40; i++) {
          sb.append(" ");
        }
        sb.append(" : " + entry.getValue() + "\n");

      }
      sb.append("------------------------------------------------------------------------------\n");
      sb.append("Passed list " + "\n");
      //sb.append("Passed case List: " + "\n");
      sb.append(passedDescriptionList.toString());
      sb.append("------------------------------------------------------------------------------\n");
      sb.append("Failed list: " + "\n");
      //sb.append("Failed case List: " + "\n");
      sb.append(failedDescriptionList.toString());
      sb.append("------------------------------------------------------------------------------\n");
      sb.append("Skipped list: " + "\n");
      //sb.append("Skipped case List: " + "\n");
      sb.append(skippedDescriptionList.toString());
      sb.append("----------------------------------------------------------------\n");

      String res = sb.toString();
      try {
        Files.write((Paths.get(reportPath)), res.getBytes("utf-8"), StandardOpenOption.APPEND);
      } catch (IOException e) {
        e.printStackTrace();
      }

    }

  }

  /**
   * calculate transaction num and transaction type After DailyBuild.
   */
  public List<Map.Entry<String, Integer>> calculateAfterDailyBuild() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
            .usePlaintext()
            .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    endBlockNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
            .getBlockHeader().getRawData().getNumber();
    System.out.println("-----startnum :" + startBlockNum + "-----endnum:" + endBlockNum);
    if(endBlockNum >= 8000) {
      System.out.println("Not daily build env, skip this step");
      return new ArrayList<>();
    }
    List<Protocol.Transaction> listTrans;
    List<Protocol.Transaction.Contract> listContract;
    Protocol.Block block;
    int transNum;
    int contractNum;
    String contractType;
    for (long i = startBlockNum; i < endBlockNum; i++) {
      block = PublicMethed.getBlock(i, blockingStubFull);
      listTrans = block.getTransactionsList();
      transNum = block.getTransactionsCount();
      totalTransactionNum.getAndAdd(transNum);
      for (int j = 0; j < transNum; j++) {
        listContract = listTrans.get(j).getRawData().getContractList();
        contractNum = listContract.size();
        for (int k = 0; k < contractNum; k++) {
          contractType = listContract.get(k).getType().toString();
          transactionType.put(contractType, transactionType.getOrDefault(contractType, 0) + 1);
        }
      }
    }
    try {
      if (channelFull != null) {
        channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    List<Map.Entry<String, Integer>> list = new ArrayList<>(transactionType.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
      @Override
      public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
        return (o2.getValue()).compareTo(o1.getValue());
      }
    });
    return list;
  }

}

