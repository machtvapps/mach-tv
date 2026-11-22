use std::future::Future;
use std::sync::LazyLock;
use std::sync::mpsc as std_mpsc;
use std::time::Duration;

use futures::FutureExt;
use futures::future::BoxFuture;
use tokio::runtime::Runtime;
use tokio::sync::mpsc::{UnboundedSender, unbounded_channel};

pub static RUNTIME: LazyLock<Runtime> = LazyLock::new(|| {
    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .thread_name("machtv-core")
        .build()
        .expect("tokio runtime failed to start")
});

static SEQUENTIAL: LazyLock<UnboundedSender<BoxFuture<'static, ()>>> = LazyLock::new(|| {
    let (sender, mut receiver) = unbounded_channel::<BoxFuture<'static, ()>>();
    RUNTIME.spawn(async move {
        while let Some(future) = receiver.recv().await {
            future.await;
        }
    });
    sender
});

pub fn spawn_concurrent(future: impl Future<Output = ()> + Send + 'static) {
    RUNTIME.spawn(future);
}

pub fn spawn_sequential(future: impl Future<Output = ()> + Send + 'static) {
    SEQUENTIAL
        .send(future.boxed())
        .expect("sequential queue closed");
}

pub fn drain_sequential(timeout: Duration) -> Result<(), String> {
    let (done, finished) = std_mpsc::channel();
    spawn_sequential(async move {
        let _ = done.send(());
    });
    finished
        .recv_timeout(timeout)
        .map_err(|_| "queued storage writes did not finish in time".to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Mutex};

    #[test]
    fn sequential_futures_complete_in_submission_order() {
        let order = Arc::new(Mutex::new(Vec::new()));
        for index in 0..50usize {
            let order = order.clone();
            spawn_sequential(async move {
                tokio::task::yield_now().await;
                order.lock().unwrap().push(index);
            });
        }
        drain_sequential(Duration::from_secs(5)).unwrap();
        assert_eq!(*order.lock().unwrap(), (0..50).collect::<Vec<_>>());
    }
}
