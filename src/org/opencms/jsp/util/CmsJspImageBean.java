/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH & Co. KG (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.jsp.util;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.loader.CmsImageScaler;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsCollectionsGenericWrapper;
import org.opencms.util.CmsRequestUtil;
import org.opencms.util.CmsUriSplitter;

import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.collections.Transformer;
import org.apache.commons.logging.Log;

/**
 * Bean containing image information for the use in JSP (for example formatters).
 */
public class CmsJspImageBean {

    /**
     * Provides a Map to access hi-DPI versions of the current image.<p>
     */
    public class CmsScaleHiDpiTransformer implements Transformer {

        /**
         * @see org.apache.commons.collections.Transformer#transform(java.lang.Object)
         */
        @Override
        public Object transform(Object input) {

            return createHiDpiVariation(String.valueOf(input));
        }
    }

    /**
     * Provides a Map to access ratio scaled versions of the current image.<p>
     */
    public class CmsScaleRatioTransformer implements Transformer {

        /**
         * @see org.apache.commons.collections.Transformer#transform(java.lang.Object)
         */
        @Override
        public Object transform(Object input) {

            return createRatioVariation(String.valueOf(input));
        }
    }

    /**
     * Provides a Map to access width scaled versions of the current image.<p>
     */
    public class CmsScaleWidthTransformer implements Transformer {

        /**
         * @see org.apache.commons.collections.Transformer#transform(java.lang.Object)
         */
        @Override
        public Object transform(Object input) {

            return createWidthVariation(String.valueOf(input));
        }
    }

    /** The minimum dimension (width and height) a generated image must have. */
    public static int MIN_DIMENSION = 4;

    /** The log object for this class. */
    static final Log LOG = CmsLog.getLog(CmsJspImageBean.class);

    /** The CmsResource for this image. */
    CmsResource m_resource = null;

    /** Lazy initialized map of ratio scaled versions of this image. */
    Map<String, CmsJspImageBean> m_scaleRatio = null;

    /** Lazy initialized map of width scaled versions of this image. */
    Map<String, CmsJspImageBean> m_scaleWidth = null;

    /** Map used for creating a image source set. */
    Map<Integer, CmsJspImageBean> m_srcSet = null;

    /** The CmsImageScaler that describes the basic adjustments (usually cropping) that have been set on the original image. */
    private CmsImageScaler m_baseScaler;

    /** The current OpenCms user context. */
    private CmsObject m_cms = null;

    /** The CmsImageScaler that is used to create a scaled version of this image. */
    private CmsImageScaler m_currentScaler;

    /**
     * Map used to store hi-DPI variants of the image.
     * <ul>
     *   <li>key: the variant multiplier, e.g. "2x" (the common retina multiplier)</li>
     *   <li>value: a CmsJspImageBean representing the hi-DPI variant</li>
     * </ul>
     */
    private Map<String, CmsJspImageBean> m_hiDpiImages = null;

    /** The CmsImageScaler that describes the original pixel proportions of this image. */
    private CmsImageScaler m_originalScaler;

    /** The image quality (for JPEG calculation). */
    private int m_quality = 0;

    /** The image VFS path. */
    private String m_vfsUri;

    /**
     * Initializes a new image bean based on a VFS input string.<p>
     *
     * The input string is is used to read the images from the VFS.
     * It can contain scaling parameters.<p>
     *
     * @param cms the current uses OpenCms context
     * @param imageUri the URI to read the image from in the OpenCms VFS, may also contain scaling parameters
     *
     * @throws CmsException in case of problems reading the image from the VFS
     */
    public CmsJspImageBean(CmsObject cms, String imageUri)
    throws CmsException {

        setCmsObject(cms);
        // split the given image URI to see if there are scaling parameters attached
        CmsUriSplitter splitSrc = new CmsUriSplitter(imageUri);
        String scaleParam = null;
        if (splitSrc.getQuery() != null) {
            // check if the original URI already has parameters, this is true if original has been cropped
            String[] scaleStr = CmsRequestUtil.createParameterMap(splitSrc.getQuery()).get(CmsImageScaler.PARAM_SCALE);
            if (scaleStr != null) {
                scaleParam = scaleStr[0];
            }
        }

        // set VFS URI without scaling parameters
        setVfsUri(splitSrc.getPrefix());
        setResource(cms.readResource(getVfsUri()));

        // the originalScaler reads the image dimensions from the VFS properties
        CmsImageScaler originalScaler = new CmsImageScaler(cms, getResource());
        // set original scaler
        setOriginalScaler(originalScaler);

        // set base scaler
        CmsImageScaler baseScaler = originalScaler;
        if (scaleParam != null) {
            // scale parameters have been set
            baseScaler = new CmsImageScaler(scaleParam);
        }
        setBaseScaler(baseScaler);

        // set the current scaler to the base scaler
        setScaler(baseScaler);
    }

    /**
     * Initializes a new image bean based on a VFS input string and applies additional re-scaling.<p>
     *
     * The input string is is used to read the images from the VFS.
     * It can contain scaling parameters.
     * The additional re-scaling is then applied to the image that has been read.<p>
     *
     * @param cms the current uses OpenCms context
     * @param imageUri the URI to read the image from in the OpenCms VFS, may also contain scaling parameters
     * @param initScaler the additional re-scaling to apply to the image
     *
     * @throws CmsException in case of problems reading the image from the VFS
     */
    public CmsJspImageBean(CmsObject cms, String imageUri, CmsImageScaler initScaler)
    throws CmsException {

        this(cms, imageUri);

        CmsImageScaler targetScaler = createVariation(
            getWidth(),
            getHeight(),
            getBaseScaler(),
            initScaler.getWidth(),
            initScaler.getHeight(),
            getQuality());

        if ((targetScaler != null) && targetScaler.isValid()) {
            setScaler(targetScaler);
        }
    }

    /**
     * Initializes a new empty image bean.<p>
     *
     * All values must be set with setters later.<p>
     */
    protected CmsJspImageBean() {

        // all values must be set with setters later
    }

    /**
     * Create a variation scaler fir this image.<p>
     *
     * @param originalWidth the original image pixel width
     * @param originalHeight the original image pixel height
     * @param baseScaler the base scaler that may contain crop parameters
     * @param targetWidth the target image pixel width
     * @param targetHeight the target image pixel height
     * @param quality the compression quality factor to use for image generation
     *
     * @return the created variation scaler for this image
     */
    protected static CmsImageScaler createVariation(
        int originalWidth,
        int originalHeight,
        CmsImageScaler baseScaler,
        int targetWidth,
        int targetHeight,
        int quality) {

        CmsImageScaler result = null;

        if ((targetWidth <= 0) || (targetHeight <= 0)) {
            // not all dimensions have been given, calculate the missing

            double baseRatio;
            if (baseScaler.isCropping()) {
                // use the image crop with/height for aspect ratio calculation
                baseRatio = (double)baseScaler.getCropWidth() / (double)baseScaler.getCropHeight();
            } else {
                // use the image original pixel width/height for aspect ratio calculation
                baseRatio = (double)originalWidth / (double)originalHeight;
            }

            // one dimension is missing, calculate it from the image
            if (targetWidth <= 0) {
                // width is not set, calculate it with the given height and the aspect ratio
                targetWidth = (int)Math.round(targetHeight * baseRatio);
            } else if (targetHeight <= 0) {
                // height is not set, calculate it with the given width and the aspect ratio
                targetHeight = (int)Math.round(targetWidth / baseRatio);
            }
        }

        if ((targetWidth >= MIN_DIMENSION)
            && (targetHeight >= MIN_DIMENSION)
            && (originalWidth >= targetWidth)
            && (originalHeight >= targetHeight)) {

            // image original dimensions are large enough, generate result scaler
            result = new CmsImageScaler();

            result.setWidth(targetWidth);
            result.setHeight(targetHeight);
            result.setType(2);

            if (baseScaler.isCropping()) {

                double targetRatio = (double)baseScaler.getCropWidth() / (double)targetWidth;
                int targetCropWidth = baseScaler.getCropWidth();
                int targetCropHeight = (int)Math.round(targetHeight * targetRatio);

                if (targetCropHeight > baseScaler.getCropHeight()) {
                    targetRatio = (double)baseScaler.getCropHeight() / (double)targetHeight;
                    targetCropWidth = (int)Math.round(targetWidth * targetRatio);
                    targetCropHeight = baseScaler.getCropHeight();
                }

                int targetX = baseScaler.getCropX();
                int targetY = baseScaler.getCropY();

                if (targetCropWidth != baseScaler.getCropWidth()) {
                    targetX = targetX + (int)Math.round((baseScaler.getCropWidth() - targetCropWidth) / 2.0);
                }
                if (targetCropHeight != baseScaler.getCropHeight()) {
                    targetY = targetY + (int)Math.round((baseScaler.getCropHeight() - targetCropHeight) / 2.0);
                }

                result.setCropArea(targetX, targetY, targetCropWidth, targetCropHeight);
            }
        }

        if ((result != null) && (quality > 0)) {
            // apply compression quality setting
            result.setQuality(quality);
        }

        return result;
    }

    /**
     * adds a CmsJspImageBean as hi-DPI variant to this image
     * @param factor the variant multiplier, e.g. "2x" (the common retina multiplier)
     * @param image the image to be used for this variant
     */
    public void addHiDpiImage(String factor, CmsJspImageBean image) {

        if (m_hiDpiImages == null) {
            m_hiDpiImages = CmsCollectionsGenericWrapper.createLazyMap(new CmsScaleHiDpiTransformer());
        }
        m_hiDpiImages.put(factor, image);
    }

    /**
     * Creates a hi-DPI scaled version of the current image.<p>
     *
     * @param hiDpiStr the hi-DPI variation to generate, for example "2.5x".<p>
     *
     * @return a hi-DPI scaled version of the current image
     */
    public CmsJspImageBean createHiDpiVariation(String hiDpiStr) {

        CmsJspImageBean result = null;
        if (hiDpiStr.matches("^[0-9]+(.[0-9]+)?x$")) {

            double multiplier = Double.valueOf(hiDpiStr.substring(0, hiDpiStr.length() - 1)).doubleValue();

            int targetWidth = (int)Math.round(getScaler().getWidth() * multiplier);
            int targetHeight = (int)Math.round(getScaler().getHeight() * multiplier);

            CmsImageScaler targetScaler = createVariation(
                getWidth(),
                getHeight(),
                getBaseScaler(),
                targetWidth,
                targetHeight,
                getQuality());

            if (targetScaler != null) {
                result = createVariation(targetScaler);
            }

        } else {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Illegal multiplier format: '%s' not usable for image scaling", hiDpiStr));
            }
        }
        return result;
    }

    /**
     * Creates a ratio scaled version of the current image.<p>
     *
     * @param ratioStr the rato variation to generate, for example "4-3" or "1-1".<p>
     *
     * @return a ratio scaled version of the current image
     */
    public CmsJspImageBean createRatioVariation(String ratioStr) {

        CmsJspImageBean result = null;

        try {
            int i = ratioStr.indexOf('-');
            if (i > 0) {
                ratioStr = ratioStr.replace(',', '.');

                double ratioW = Double.valueOf(ratioStr.substring(0, i)).doubleValue();
                double ratioH = Double.valueOf(ratioStr.substring(i + 1)).doubleValue();

                int targetWidth, targetHeight;

                double ratioFactorW = getScaler().getWidth() / ratioW;
                targetWidth = getScaler().getWidth();
                targetHeight = (int)Math.round(ratioH * ratioFactorW);

                if (targetHeight > getScaler().getHeight()) {
                    double ratioFactorH = getScaler().getHeight() / ratioH;
                    targetWidth = (int)Math.round(ratioW * ratioFactorH);
                    targetHeight = getScaler().getHeight();
                }

                CmsImageScaler targetScaler = createVariation(
                    getWidth(),
                    getHeight(),
                    getBaseScaler(),
                    targetWidth,
                    targetHeight,
                    getQuality());

                if (targetScaler != null) {
                    result = createVariation(targetScaler);
                }
            }
        } catch (NumberFormatException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Illegal ratio format: '%s' not usable for image scaling", ratioStr));
            }
        }

        return result;
    }

    /**
     * Creates a width scaled version of the current image.<p>
     *
     * @param widthStr the with variation to generate, for example "1078" or "800".<p>
     *
     * @return a width scaled version of the current image
     */
    public CmsJspImageBean createWidthVariation(String widthStr) {

        CmsJspImageBean result = null;

        try {

            double baseRatio;
            if (getScaler().isCropping()) {
                // use the image crop with/height for aspect ratio calculation
                baseRatio = (double)getScaler().getCropWidth() / (double)getScaler().getCropHeight();
            } else {
                // use the image original pixel width/height for aspect ratio calculation
                baseRatio = (double)getScaler().getWidth() / (double)getScaler().getHeight();
            }

            // height is not set, calculate it with the given width and the aspect ratio
            int targetWidth = Integer.valueOf(widthStr).intValue();
            int targetHeight = (int)Math.round(targetWidth / baseRatio);

            CmsImageScaler targetScaler = createVariation(
                getWidth(),
                getHeight(),
                getBaseScaler(),
                targetWidth,
                targetHeight,
                getQuality());

            if (targetScaler != null) {
                result = createVariation(targetScaler);
            }

        } catch (NumberFormatException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Illegal width format: '%s' not usable for image scaling", widthStr));
            }
        }

        return result;
    }

    /**
     * Returns the original pixel height of the image.<p>
     *
     * @return the original pixel height of the image
     */
    public int getHeight() {

        return m_originalScaler.getHeight();
    }

    /**
     * Returns a lazy initialized Map that provides access to ratio scaled instances of this image bean.<p>
     *
     * @return a lazy initialized Map that provides access to ratio scaled instances of this image bean
     *
     * @deprecated use {@link #getScaleHiDpi()} instead
     */
    @Deprecated
    public Map<String, CmsJspImageBean> getHiDpiImages() {

        return getScaleHiDpi();
    }

    /**
     * Returns the basic source parameters for this image.<p>
     *
     * In case the image was cropped or otherwise manipulated,
     * the values are created for the manipulated version.<p>
     *
     * The return form is "src='(srcUrl)' height='(h)' width='(w)'".<p>
     *
     * @return the basic source parameters for this image
     */
    public String getImgSrc() {

        StringBuffer result = new StringBuffer(128);

        // append the image source
        result.append("src=\"");
        result.append(getSrcUrl());
        result.append("\"");
        // append image width and height
        result.append(" width=\"");
        result.append(m_currentScaler.getWidth());
        result.append("\"");
        result.append(" height=\"");
        result.append(m_currentScaler.getHeight());
        result.append("\"");

        return result.toString();
    }

    /**
     * Returns the compression quality factor used for image generation.<p>
     *
     * @return the compression quality factor used for image generation
     */
    public int getQuality() {

        return m_quality;
    }

    /**
     * Returns a lazy initialized Map that provides access to hi-DPI scaled instances of this image bean.<p>
     *
     * <ul>
     *   <li>key: the variant multiplier, e.g. "2x" (the common retina multiplier)</li>
     *   <li>value: a CmsJspImageBean representing the hi-DPI variant</li>
     * </ul>
     *
     * @return a lazy initialized Map that provides access to hi-DPI scaled instances of this image bean
     */
    public Map<String, CmsJspImageBean> getScaleHiDpi() {

        if (m_hiDpiImages == null) {
            m_hiDpiImages = CmsCollectionsGenericWrapper.createLazyMap(new CmsScaleHiDpiTransformer());
        }
        return m_hiDpiImages;
    }

    /**
     * Returns the image scaler that is used for the scaled version of this image bean.<p>
     *
     * @return the image scaler that is used for the scaled version of this image bean
     */
    public CmsImageScaler getScaler() {

        return m_currentScaler;
    }

    /**
     * Returns a lazy initialized Map that provides access to ratio scaled instances of this image bean.<p>
     *
     * @return a lazy initialized Map that provides access to ratio scaled instances of this image bean
     */
    public Map<String, CmsJspImageBean> getScaleRatio() {

        if (m_scaleRatio == null) {
            m_scaleRatio = CmsCollectionsGenericWrapper.createLazyMap(new CmsScaleRatioTransformer());
        }
        return m_scaleRatio;
    }

    /**
     * Returns a lazy initialized Map that provides access to width scaled instances of this image bean.<p>
     *
     * @return a lazy initialized Map that provides access to width scaled instances of this image bean
     */
    public Map<String, CmsJspImageBean> getScaleWidth() {

        if (m_scaleWidth == null) {
            m_scaleWidth = CmsCollectionsGenericWrapper.createLazyMap(new CmsScaleWidthTransformer());
        }
        return m_scaleWidth;
    }

    /**
     * Generates a srcset attribute parameter list from all images added to this image bean.<p>
     *
     * @return a srcset attribute parameter list from all images added to this image bean
     */
    public String getSrcSet() {

        StringBuffer result = new StringBuffer(128);
        if (m_srcSet != null) {
            int items = m_srcSet.size();
            for (Map.Entry<Integer, CmsJspImageBean> entry : m_srcSet.entrySet()) {
                CmsJspImageBean imageBean = entry.getValue();
                // append the image source
                result.append(imageBean.getSrcUrl());
                result.append(" ");
                // append width
                result.append(imageBean.getScaler().getWidth());
                result.append("w");
                if (--items > 0) {
                    result.append(", ");
                }
            }
        }
        return result.toString();
    }

    /**
     * Generates a srcset attribute parameter for this image bean.<p>
     *
     * @return a srcset attribute parameter for this image bean
     */
    public String getSrcSetEntry() {

        StringBuffer result = new StringBuffer(128);
        if (m_currentScaler.isValid()) {
            // append the image source
            result.append(getSrcUrl());
            result.append(" ");
            // append width
            result.append(m_currentScaler.getWidth());
            result.append("w");
        }
        return result.toString();
    }

    /**
     * Getter for {@link #setSrcSets(CmsJspImageBean)} which returns this image bean.<p>
     *
     * Exists to make sure {@link #setSrcSets(CmsJspImageBean)} is available as property on a JSP.<p>
     *
     * @return this image bean
     *
     * @see CmsJspImageBean#getSrcSet()
     */
    public CmsJspImageBean getSrcSets() {

        return this;
    }

    /**
     * Returns the image URL that may be used in img or picture tags.<p>
     *
     * @return the image URL
     */
    public String getSrcUrl() {

        String imageSrc = getCmsObject().getSitePath(getResource());
        if ((getScaler() != null) && getScaler().isValid()) {
            // now append the scaler parameters if required
            imageSrc += getScaler().toRequestParam();
        }
        return OpenCms.getLinkManager().substituteLink(getCmsObject(), imageSrc);
    }

    /**
     * Returns the URI of the image in the OpenCms VFS.<p>
     *
     * @return the URI of the image in the OpenCms VFS
     */
    public String getVfsUri() {

        return m_vfsUri;
    }

    /**
     * Returns the original (unscaled) width of the image.<p>
     *
     * @return the original (unscaled) width of the image
     */
    public int getWidth() {

        return m_originalScaler.getWidth();
    }

    /**
     * Returns <code>true</code> if the image has been scaled or otherwise processed.<p>
     *
     * @return the isScaled
     */
    public boolean isScaled() {

        return !m_currentScaler.isOriginalScaler();
    }

    /**
     * Sets the compression quality factor to use for image generation.<p>
     *
     * @param quality the compression quality factor to use for image generation
     */
    public void setQuality(int quality) {

        m_quality = quality;
    }

    /**
     * Adds another image bean instance to the source set map of this bean.<p>
     *
     * @param imageBean the image bean to add
     */
    public void setSrcSets(CmsJspImageBean imageBean) {

        if (m_srcSet == null) {
            m_srcSet = new TreeMap<Integer, CmsJspImageBean>();
        }
        if ((imageBean != null) && imageBean.getScaler().isValid()) {
            m_srcSet.put(Integer.valueOf(imageBean.getScaler().getWidth()), imageBean);
        }
    }

    /**
     * Sets the URI of the image in the OpenCms VFS.<p>
     *
     * @param vfsUri the URI of the image in the OpenCms VFS to set
     */
    public void setVfsUri(String vfsUri) {

        m_vfsUri = vfsUri;
    }

    /**
     * Returns a variation of the current image scaled with the given scaler.<p>
     *
     * It is always the original image which is used as a base, never a scaled version.
     * So for example if the image has been cropped by the user, the cropping are is ignored.<p>
     *
     * @param targetScaler contains the information about how to scale the image
     *
     * @return a variation of the current image scaled with the given scaler
     */
    protected CmsJspImageBean createVariation(CmsImageScaler targetScaler) {

        CmsJspImageBean result = new CmsJspImageBean();

        result.setCmsObject(getCmsObject());
        result.setResource(getResource());
        result.setOriginalScaler(getOriginalScaler());
        result.setBaseScaler(getBaseScaler());
        result.setVfsUri(getVfsUri());
        result.setScaler(targetScaler);
        result.setQuality(getQuality());

        return result;
    }

    /**
     * Sets the scaler that describes the basic adjustments (usually cropping) that have been set on the original image.<p>
     *
     * @return the scaler that describes the basic adjustments (usually cropping) that have been set on the original image
     */
    protected CmsImageScaler getBaseScaler() {

        return m_baseScaler;
    }

    /**
     * Returns the current OpenCms user context.<p>
     *
     * @return the current OpenCms user context
     */
    protected CmsObject getCmsObject() {

        return m_cms;
    }

    /**
     * Returns the image scaler that describes the original proportions of this image.<p>
     *
     * @return the image scaler that describes the original proportions of this image
     */
    protected CmsImageScaler getOriginalScaler() {

        return m_originalScaler;
    }

    /**
     * Returns the CmsResource for this image.<p>
     *
     * @return the CmsResource for this image
     */
    protected CmsResource getResource() {

        return m_resource;
    }

    /**
     * Returns this instance bean, required for the transformers.<p>
     *
     * @return this instance bean
     */
    protected CmsJspImageBean getSelf() {

        return this;
    }

    /**
     * Returns the scaler that describes the basic adjustments (usually cropping) that have been set on the original image.<p>
     *
     * @param baseScaler the scaler that describes the basic adjustments (usually cropping) that have been set on the original image
     */
    protected void setBaseScaler(CmsImageScaler baseScaler) {

        m_baseScaler = baseScaler;
    }

    /**
     * Sets the current OpenCms user context.<p>
     *
     * @param cms the current OpenCms user context to set
     */
    protected void setCmsObject(CmsObject cms) {

        m_cms = cms;
    }

    /**
     * Sets the scaler that describes the original proportions of this image.<p>
     *
     * @param originalScaler the scaler that describes the original proportions of this image
     */
    protected void setOriginalScaler(CmsImageScaler originalScaler) {

        m_originalScaler = originalScaler;
    }

    /**
     * Sets the CmsResource for this image.<p>
     *
     * @param resource the CmsResource for this image
     */
    protected void setResource(CmsResource resource) {

        m_resource = resource;
    }

    /**
     * Sets the image scaler that was used to create this image.<p>
     *
     * @param scaler the image scaler that was used to create this image.
     */
    protected void setScaler(CmsImageScaler scaler) {

        m_currentScaler = scaler;
    }
}