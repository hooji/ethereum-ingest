package com.codingchili.ethereumingest.importer;

import com.codingchili.core.context.CoreContext;
import com.codingchili.core.protocol.Serializer;
import com.codingchili.core.storage.AsyncStorage;
import com.codingchili.ethereumingest.model.ApplicationConfig;
import com.codingchili.ethereumingest.model.BlockLogListener;
import com.codingchili.ethereumingest.model.ImportListener;
import com.codingchili.ethereumingest.model.Importer;
import com.codingchili.ethereumingest.model.StorableBlock;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import rx.Observable;
import rx.Subscriber;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.codingchili.core.configuration.CoreStrings.ID_TIME;
import static com.codingchili.ethereumingest.importer.ApplicationContext.TX_ADDR;
import static com.codingchili.ethereumingest.importer.ApplicationContext.getIpcClient;

/**
 * A service that reads block data from an Ethereum client IPC connection.
 * <p>
 * If #{@link ApplicationConfig#txImport} is set to true will request full
 * transaction details in each block. The transaction data is forwarded to
 * another handler that imports the transactions into the configured storage.
 * <p>
 * If #{@link ApplicationConfig#blockImport} this service will also import
 * block data into the configured storage.
 */
public class BlockService implements Importer {
    private static final String BLOCK_RETRY_TIMER = "blockRetryTimer";
    private static final int ONE_MINUTE = 60000;
    private static final int ONE_SECOND = 1000;
    private AtomicBoolean stopping = new AtomicBoolean(false);
    private AtomicLong timerId = new AtomicLong(0);
    private AtomicInteger queue = new AtomicInteger(0);
    private ApplicationConfig config = ApplicationConfig.get();
    private AsyncStorage<StorableBlock> storage;
    private ImportListener listener;
    private ApplicationContext context;

    @Override
    public void init(CoreContext core) {
        this.context = new ApplicationContext(core);

        if (listener == null) {
            listener = new BlockLogListener(core.logger(getClass()));
        }
    }

    @Override
    public void start(Future<Void> future) {
        context.blockStorage().setHandler(done -> {

            if (done.succeeded()) {
                future.complete();
                storage = done.result();
                Web3j client = getIpcClient();
                Integer start = Integer.parseInt(config.getStartBlock());
                Integer end = Integer.parseInt(config.getBlockEnd());

                Observable.range(start, end - start).subscribe(new Subscriber<Integer>() {
                    final AtomicReference<String> hash = new AtomicReference<>();

                    @Override
                    public void onStart() {
                        request(config.getBackpressureBlocks());
                    }

                    @Override
                    public void onCompleted() {
                        listener.onFinished();
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (!stopping.get()) {
                            listener.onError(e, hash.get());
                            unsubscribe();
                            stopping.set(true);
                        }
                    }

                    @Override
                    public void onNext(Integer blockNum) {
                        Consumer<EthBlock> importer = eth -> {
                            hash.set(eth.getBlock().getHash());
                            startImport(eth).setHandler(done1 -> {
                                if (done1.succeeded()) {
                                    request(1);
                                } else {
                                    onError(done1.cause());
                                }
                            });
                        };

                        Supplier<EthBlock> chain = () -> {
                            DefaultBlockParameterNumber param = new DefaultBlockParameterNumber(Long.valueOf(blockNum + ""));
                            // prevent json corruption over the ipc connection.
                            synchronized (BlockService.class) {
                                try {
                                    return client.ethGetBlockByNumber(param, config.isTxImport()).send();
                                } catch (IOException e) {
                                    onError(e);
                                    return null;
                                }
                            }
                        };

                        // avoid blocking the event loop while waiting for the ipc.
                        context.blocking(exec -> {
                            if (!stopping.get()) {
                                EthBlock event = chain.get();
                                if (event.getBlock() == null) {
                                    listener.onSourceDepleted();

                                    // block does not exist yet, wait a bit.
                                    context.periodic(() -> ONE_SECOND, BLOCK_RETRY_TIMER, done -> {
                                        timerId.set(done);
                                        EthBlock retry = chain.get();
                                        if (retry.getBlock() != null) {
                                            context.cancel(done);
                                            importer.accept(retry);
                                        }
                                    });
                                } else {
                                    importer.accept(event);
                                }
                                exec.complete();
                            }
                        }, (done) -> {
                        });
                    }
                });
            } else {
                future.fail(done.cause());
            }
        });
    }

    @Override
    public void stop(Future<Void> stop) {
        if (timerId.get() != 0) {
            context.cancel(timerId.get());
        }
        stopping.set(true);
        stop.complete();
    }

    private CompositeFuture startImport(EthBlock fullBlock) {
        StorableBlock block = new StorableBlock(fullBlock.getBlock());
        listener.onImportStarted(block.getHash(), block.getNumber());
        return CompositeFuture.all(
                importBlocks(block),
                importTx(block, getTransactionList(fullBlock))
        );
    }

    private Future<Void> importBlocks(StorableBlock block) {
        Future<Void> future = Future.future();

        if (config.isTxImport()) {
            listener.onQueueChanged(queue.incrementAndGet());

            storage.put(block, done -> {
                if (done.succeeded()) {
                    if (!config.isTxImport()) {
                        listener.onImported(block.getHash(), block.getNumber());
                    }
                    future.complete();
                } else {
                    future.fail(done.cause());
                }
                listener.onQueueChanged(queue.decrementAndGet());
            });
        } else {
            future.complete();
        }
        return future;
    }

    private Future<Void> importTx(StorableBlock block, JsonObject transactions) {
        Future<Void> future = Future.future();
        DeliveryOptions delivery = new DeliveryOptions().setSendTimeout(ONE_MINUTE);

        if (config.isTxImport()) {
            context.bus().send(TX_ADDR, transactions, delivery, done -> {
                listener.onImported(block.getHash(), block.getNumber());
                if (done.succeeded()) {
                    Throwable result = (Throwable) done.result().body();
                    if (result == null) {
                        future.complete();
                    } else {
                        future.fail(result);
                    }
                } else {
                    future.fail(done.cause());
                }
            });
        } else {
            future.complete();
        }
        return future;
    }

    private JsonObject getTransactionList(EthBlock ethBlock) {
        JsonObject json = Serializer.json(ethBlock.getBlock().getTransactions());
        json.put(ID_TIME, ethBlock.getBlock().getTimestamp().longValue());
        return json;
    }

    @Override
    public Importer setListener(ImportListener listener) {
        this.listener = listener;
        return this;
    }
}