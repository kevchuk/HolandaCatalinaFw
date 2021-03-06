package org.hcjf.layers;

import org.hcjf.layers.crud.CrudLayerInterface;
import org.hcjf.layers.storage.StorageLayerInterface;
import org.hcjf.service.ServiceSession;
import org.hcjf.service.ServiceThread;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * All the layer implementation extends this class, and this class is a proxy
 * between the layer client and implementation.
 * @author javaito
 */
public abstract class Layer implements LayerInterface {

    private final String implName;
    private final boolean stateful;

    /**
     * This is the end point for all the layers constructor.
     * @param implName Implementation name.
     * @param stateful Stateful status.
     */
    public Layer(String implName, boolean stateful) {
        this.implName = implName;
        this.stateful = stateful;
    }

    public Layer(String implName) {
        this(implName, true);
    }

    public Layer(boolean stateful) {
        this(null, stateful);
    }

    public Layer(){
        this(null, true);
    }

    /**
     * Return the layer implementation name.
     * @return Layer implementation name.
     */
    @Override
    public String getImplName() {
        return implName;
    }

    /**
     * Return if the layer is stateful or not.
     * @return Stateful
     */
    @Override
    public final boolean isStateful() {
        return stateful;
    }

    /**
     * Return the string set with all the aliases for this implementation.
     * @return Aliases for this implementation.
     */
    public Set<String> getAliases() {
        return null;
    }

    /**
     * This method return true if the layer instance is overwritable for other instance
     * whit the same name.
     * @return True if the layer is overwritable or false in the otherwise.
     */
    public boolean isOverwritable() {
        return true;
    }

    /**
     * This method must be override to add restrictions over particular
     * implementations of the layers.
     * @return Access object.
     */
    protected Access checkAccess(){
        return new Access(true);
    }

    /**
     * Delegation method to get some layer implementation.
     * @param layerClass Layer implementation class.
     * @param implName Layer implementation name.
     * @param <L> Expected layer implementation class.
     * @return Layer implementation.
     */
    protected final <L extends LayerInterface> L getLayer(Class<? extends L> layerClass, String implName) {
        return Layers.get(layerClass, implName);
    }

    /**
     * Delegation method to get some crud layer implementation.
     * @param implName Layer implementation name.
     * @param <L> Expected crud layer implementation class.
     * @return Crud layer implementation.
     */
    protected final <L extends CrudLayerInterface<?>> L getCrudLayer(String implName) {
        return (L) getLayer(CrudLayerInterface.class, implName);
    }

    /**
     * Delegation method to get some storage layer implementation.
     * @param implName Layer implementation name.
     * @param <L> Expected storage layer implementation class.
     * @return Storage layer implementation.
     */
    protected final <L extends StorageLayerInterface<?>> L getStorageLayer(String implName) {
        return (L) getLayer(StorageLayerInterface.class, implName);
    }

    /**
     * Return the layer proxy of the layer or null by default.
     * @return Layer proxy instance.
     */
    public LayerProxy getProxy() {
        return new LayerProxy() {
            @Override
            public ProxyInterceptor onBeforeInvoke(Method method, Object... params) {return null;}

            @Override
            public void onAfterInvoke(Method method, Object result, Object... params) {}
        };
    }

    /**
     * This method intercepts the call to layer implementation and
     * save some information about the thread behavior.
     * @param proxy Object to be called.
     * @param method Method to be called.
     * @param args Method to invoke the method.
     * @return Return the value returned for the proxy method.
     * @throws Throwable Throw all the generated exceptions.
     */
    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Access access = checkAccess();

        if(access == null) {
            throw new SecurityException("Access null");
        }
        if(!access.granted) {
            if(access.message != null && access.getThrowable() != null) {
                throw new SecurityException(access.getMessage(), access.getThrowable());
            } else if(access.getMessage() != null) {
                throw new SecurityException(access.getMessage());
            } else if(access.getThrowable() != null) {
                throw new SecurityException(access.getThrowable());
            }
        }

        ServiceThread serviceThread = null;
        if(Thread.currentThread() instanceof ServiceThread) {
            serviceThread = (ServiceThread) Thread.currentThread();
            serviceThread.putLayer(getClass());
        }

        Object result;
        try {
            LayerProxy.ProxyInterceptor interceptor =getProxy().onBeforeInvoke(method, args);
            if(interceptor == null || !interceptor.isCached()) {
                result = method.invoke(getTarget(), args);
            } else {
                result = interceptor.getResult();
            }
            getProxy().onAfterInvoke(method, result, args);
        } finally {
            if(serviceThread != null) {
                serviceThread.removeLayer();
            }
        }
        return result;
    }

    /**
     * This method return the invocation target.
     * @return Invation target.
     */
    protected Object getTarget() {
        return this;
    }

    /**
     * Return the session associated to the execution thread.
     * @return Service session.
     */
    protected final ServiceSession getSession() {
        return ((ServiceThread)Thread.currentThread()).getSession();
    }

    /**
     * This class represents the access resume of the layer.
     */
    public final static class Access {

        private final boolean granted;
        private final String message;
        private final Throwable throwable;

        public Access(boolean granted, String message, Throwable throwable) {
            this.granted = granted;
            this.message = message;
            this.throwable = throwable;
        }

        public Access(boolean granted, String message) {
            this(granted, message, null);
        }

        public Access(boolean granted) {
            this(granted, null, null);
        }

        /**
         * It returns true if access has been granted and false otherwise.
         * @return Granted resume.
         */
        public boolean isGranted() {
            return granted;
        }

        /**
         * Return a message associated to the granted resume.
         * @return Message.
         */
        public String getMessage() {
            return message;
        }

        /**
         * Return the throwable associated to the granted resume.
         * @return Throwable.
         */
        public Throwable getThrowable() {
            return throwable;
        }
    }

}
