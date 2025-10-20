
public class GalleryViewModel extends ViewModel {

    // region [Album Folder]  获取系统相册，文件夹列表

    private final SingleLiveEvent<List<MediaFolderBean>> albumFoldersLiveData = new SingleLiveEvent<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Observe the album folder list results
     */
    public SingleLiveEvent<List<MediaFolderBean>> getAlbumFoldersLiveData() {
        return albumFoldersLiveData;
    }

    /**
     * Build a multi-level album folder structure
     */
    public void loadAlbumFolders(Context context) {
        if (context == null) {
            albumFoldersLiveData.postValue(new ArrayList<>());
            return;
        }

        executorService.execute(() -> {
            List<MediaItemPath> allPaths = new ArrayList<>();

            // 1. Get all media paths
            collectAllMediaPaths(context, allPaths);

            // 2. Building a multi-level folder tree
            List<MediaFolderBean> folderTree = buildFolderTree(allPaths);

            // 3. 封装顶级虚拟文件夹 ALL_RESOURCE (所有图片)

//                 * DCIM/
//                    * ├─ Camera/
//                    * │   ├─ camera_child/
//                    * │   │    └─ aaa.jpg
//                    * │   └─ bbb.jpg
//                    * ├─ Other/
//                    * │   └─ ccc.jpg
//                    * │
//                    * └─ DCIM_CHILD (虚拟文件夹)
//                    * └─ root 下的 xx.jpg
//                    *
            MediaFolderBean allResourceFolder = wrapAllResource(folderTree);
            albumFoldersLiveData.postValue(Collections.singletonList(allResourceFolder));
        });
    }

    /**
     * get all media path
     */
    private void collectAllMediaPaths(Context context, List<MediaItemPath> result) {

        // image & video
        Uri[] uris = new Uri[]{
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        };

        // params to be query
        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.MIME_TYPE
        };

        for (Uri uri : uris) {
            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor == null) continue;

                int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID);
                int bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
                int relPathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
                int dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
                int mimeTypeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String bucketId = cursor.getString(bucketIdCol);
                    String bucketName = cursor.getString(bucketNameCol);
                    String relPath = cursor.getString(relPathCol);
                    long dateTaken = cursor.getLong(dateTakenCol);
                    String mimeType = cursor.getString(mimeTypeCol);
                    Uri fileUri = ContentUris.withAppendedId(uri, id);

                    MediaItemPath path = new MediaItemPath();
                    path.bucketId = bucketId;
                    path.bucketName = bucketName;
                    path.relativePath = relPath;
                    path.uri = fileUri;
                    path.dateTaken = dateTaken;
                    path.mimeType = mimeType;
                    result.add(path);
                }
            }
        }
    }

    /**
     * Building a multi-level folder tree
     * <p>
     * DCIM/
     * ├─ Camera/
     * │   ├─ camera_child/
     * │   │    └─ aaa.jpg
     * │   └─ bbb.jpg
     * ├─ Other/
     * │   └─ ccc.jpg
     * │
     * └─ DCIM_CHILD (虚拟文件夹)
     * └─ root 下的 xx.jpg
     *
     * @return list media data
     */
    private List<MediaFolderBean> buildFolderTree(List<MediaItemPath> allPaths) {
        Map<String, MediaFolderBean> folderMap = new HashMap<>();
        // 记录每个目录下直接文件数量（不含子目录）
        Map<String, Integer> directCountMap = new HashMap<>();
        // 记录每个目录下最新的一条媒体，用于虚拟文件夹封面
        Map<String, MediaItemPath> directLatestMap = new HashMap<>();

        for (MediaItemPath item : allPaths) {
            if (item.relativePath == null) continue;

            // 1) 先按 segments 构建各级节点（和原来逻辑一致）
            String[] segments = item.relativePath.split("/"); // eg: "DCIM/Camera/"
            StringBuilder currentPath = new StringBuilder();
            String parentPath = "";

            for (String segment : segments) {
                if (segment.isEmpty()) continue;

                if (!(currentPath.length() <= 0)) { // isNotEmpty
                    currentPath.append("/");
                }
                currentPath.append(segment);

                String pathKey = currentPath.toString();

                MediaFolderBean folder = folderMap.get(pathKey);
                if (folder == null) {
                    folder = new MediaFolderBean();
                    folder.bucketId = item.bucketId;
                    folder.bucketName = segment;
                    folder.path = pathKey;
                    folder.parentPath = parentPath;
                    folder.itemCount = 0;
                    folder.children = folder.children == null ? new ArrayList<>() : folder.children;
                    folderMap.put(pathKey, folder);

                    // 建立父子关系
                    if (!parentPath.isEmpty()) {
                        MediaFolderBean parent = folderMap.get(parentPath);
                        if (parent != null) {
                            parent.children.add(folder);
                        }
                    }
                }

                // 更新封面/计数（此计数含子目录的文件，会在每个祖先节点都累加）
                folder.itemCount++;
                if (item.dateTaken > folder.latestDateTaken) {
                    folder.latestDateTaken = item.dateTaken;
                    folder.coverUri = item.uri;
                    folder.mimeType = item.mimeType;
                }

                parentPath = pathKey;
            }

            // 2) 统计“直接在该目录（relativePath）下”的文件（不包括子目录）
            // relativePath 通常以 "/" 结尾，例如 "LLC/" => 去掉尾部 "/"
            String folderKey = item.relativePath.replaceAll("/$", ""); // e.g. "LLC" or "DCIM/Camera"
            // 累加直接文件数量
            Integer orDefault = directCountMap.getOrDefault(folderKey, 0);
            if (orDefault == null) {
                orDefault = 0;
            }
            directCountMap.put(folderKey, orDefault + 1);
            // 更新该目录下的最新文件（用于虚拟文件夹封面）
            MediaItemPath existing = directLatestMap.get(folderKey);
            if (existing == null || item.dateTaken > existing.dateTaken) {
                directLatestMap.put(folderKey, item);
            }
        }

        // 处理特殊目录与虚拟子文件夹（需要 directCountMap、directLatestMap 信息）
        handleSpecialFolders(folderMap, directCountMap, directLatestMap);

        // 返回根目录列表（没有 parent 的），按名字排序
        return folderMap.values()
                .stream()
                .filter(f -> f.parentPath == null || f.parentPath.isEmpty())
                .sorted(Comparator.comparing(f -> f.bucketName == null ? "" : f.bucketName))
                .collect(Collectors.toList());
    }

    /**
     * 处理特殊目录并创建虚拟子文件夹
     *
     * @param folderMap       已构建的文件夹节点 Map（key = folder.path）
     * @param directCountMap  每个目录下直接文件的数量（key 与 folder.path 对应）
     * @param directLatestMap 每个目录下最新的 MediaItemPath（用于封面）
     */
    private void handleSpecialFolders(Map<String, MediaFolderBean> folderMap,
                                      Map<String, Integer> directCountMap,
                                      Map<String, MediaItemPath> directLatestMap) {


        for (MediaFolderBean folder : folderMap.values()) {
            // 标注该目录是否有直接媒体文件（不统计子目录）
            Integer directCount = directCountMap.getOrDefault(folder.path, 0);
            if (directCount == null) {
                directCount = 0;
            }
            folder.hasMediaInRoot = directCount > 0;
            String bucketName = folder.bucketName;

            // 若是在根目录有直接文件(eg DCIM 或 Pictures 或其他) -> 创建虚拟子文件夹 *_CHILD
            if ((bucketName != null
                    && !bucketName.equalsIgnoreCase("Download")
                    && folder.hasMediaInRoot)) {

                // 创建虚拟子文件夹
                MediaFolderBean virtualChild = new MediaFolderBean();
                virtualChild.bucketName = bucketName + "_CHILD";
                virtualChild.path = folder.path + "_CHILD";
                virtualChild.parentPath = folder.path;
                virtualChild.isVirtual = true;
                virtualChild.hasMediaInRoot = true;

                // 使用 directCountMap 填充数量（只统计该目录根下直接文件数量）
                virtualChild.itemCount = directCount;

                // 使用 directLatestMap 填充封面信息
                MediaItemPath latest = directLatestMap.get(folder.path);
                if (latest != null) {
                    virtualChild.coverUri = latest.uri;
                    virtualChild.latestDateTaken = latest.dateTaken;
                    virtualChild.mimeType = latest.mimeType;
                }

                // 把虚拟子文件夹放到 children 列表（加在首位更显眼，可按需调整）
                if (folder.children == null) folder.children = new ArrayList<>();
                folder.children.add(0, virtualChild);
            }

            // 2. Download 文件夹 -> 展平
            if (bucketName != null && bucketName.equalsIgnoreCase("Download")) {
                // Download 根展示所有直接文件
                // 所以清空 children 保持平铺展示，并把 hasMediaInRoot 标为 true
                folder.children.clear();
                folder.hasMediaInRoot = true;
                // 如果 folder.itemCount 设置为 directCount（只统计根），可以使用：
                // folder.itemCount = directCountMap.getOrDefault(folder.path, folder.itemCount);
            }
        }

        // 3. 确保 Movies 文件夹存在（即使手机没有）
        if (!folderMap.containsKey("Movies")) {
            MediaFolderBean moviesFolder = new MediaFolderBean();
            moviesFolder.bucketName = "Movies";
            moviesFolder.path = "Movies";
            moviesFolder.parentPath = "";
            moviesFolder.itemCount = 0;
            moviesFolder.children = new ArrayList<>();
            moviesFolder.hasMediaInRoot = false;
            folderMap.put("Movies", moviesFolder);
        }
    }

    /**
     * 将树形文件夹放入虚拟文件夹 ALL RESOURCE
     */
    private MediaFolderBean wrapAllResource(List<MediaFolderBean> folderTree) {
        MediaFolderBean all = new MediaFolderBean();
        all.bucketName = "ALL RESOURCE"; // 虚拟文件夹名
        all.path = "ALL_RESOURCE";
        all.bucketId = "ALL_RESOURCE";
        all.children.addAll(folderTree); // 树形结构放入 children
        all.itemCount = folderTree.stream().mapToInt(f -> f.itemCount).sum();
        all.uiRootFolderType = MediaUiRootFolderType.ALL;
        // 可以选封面：取第一个子目录的封面
        if (!folderTree.isEmpty()) {
            all.coverUri = folderTree.get(0).coverUri;
            all.mimeType = folderTree.get(0).mimeType;
            all.latestDateTaken = folderTree.get(0).latestDateTaken;
        }
        return all;
    }

    // endregion

    // region [Album Special] CAMERA DCIM ...
    private final SingleLiveEvent<List<MediaFolderBean>> specialFoldersLiveData = new SingleLiveEvent<>();

    public SingleLiveEvent<List<MediaFolderBean>> getSpecialFoldersLiveData() {
        return specialFoldersLiveData;
    }

    public void loadSpecialFolders(Context context) {
        if (context == null) {
            specialFoldersLiveData.postValue(new ArrayList<>());
            return;
        }

        executorService.execute(() -> {
            Map<String, MediaFolderBean> albumMap = new HashMap<>();
            // 图片
            String[] imageProjection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.RELATIVE_PATH
            };
            Uri imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            // 视频
            String[] videoProjection = new String[]{
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_TAKEN,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.RELATIVE_PATH
            };
            Uri videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

            queryMediaStore(context, imageUri, imageProjection, albumMap);
            queryMediaStore(context, videoUri, videoProjection, albumMap);

            // 过滤出特定一级目录（相对路径包含这些关键字）
            List<MediaFolderBean> specialFolders = albumMap.values()
                    .stream()
                    .filter(f -> f.path != null &&
                            (f.path.contains("Camera") ||
                                    f.path.contains("Movies") ||
                                    f.path.contains("Download") ||
                                    f.path.contains("Pictures")))
                    .peek(f -> {
                        if (f.path.contains("Camera"))
                            f.uiRootFolderType = MediaUiRootFolderType.CAMERA;
                        else if (f.path.contains("Movies"))
                            f.uiRootFolderType = MediaUiRootFolderType.MOVIES;
                        else if (f.path.contains("Download"))
                            f.uiRootFolderType = MediaUiRootFolderType.DOWNLOAD;
                        else if (f.path.contains("Pictures"))
                            f.uiRootFolderType = MediaUiRootFolderType.PICTURES;
                    })
                    .sorted(Comparator.comparingInt(f -> f.uiRootFolderType.ordinal()))
                    .collect(Collectors.toList());

            specialFoldersLiveData.postValue(specialFolders);

        });
    }

    // endregion

    // region [Album folder Deprecated]


    /**
     * 根据UI和条件，获取手机中的所有资源文件夹，按照 bucketId 分组
     */
    private void queryMediaStore(Context context, Uri uri, String[] projection, Map<String, MediaFolderBean> albumMap) {
        final int _ID_INDEX = 0;
        final int BUCKET_ID_INDEX = 1;
        final int BUCKET_DISPLAY_NAME_INDEX = 2;
        final int DATE_TAKEN_INDEX = 3;
        final int MIME_TYPE_INDEX = 4;
        final int RELATIVE_PATH_INDEX = 5;

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri,
                    projection,
                    null,
                    null,
                    null
            );
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(projection[_ID_INDEX]);
                int bucketIdColumn = cursor.getColumnIndexOrThrow(projection[BUCKET_ID_INDEX]);
                int bucketNameColumn = cursor.getColumnIndexOrThrow(projection[BUCKET_DISPLAY_NAME_INDEX]);
                int dateTakenColumn = cursor.getColumnIndexOrThrow(projection[DATE_TAKEN_INDEX]);
                int mimeTypeColumn = cursor.getColumnIndexOrThrow(projection[MIME_TYPE_INDEX]);
                int relativePathColum = cursor.getColumnIndexOrThrow(projection[RELATIVE_PATH_INDEX]);

                while (cursor.moveToNext()) {
                    long mediaId = cursor.getLong(idColumn);
                    String bucketId = cursor.getString(bucketIdColumn);
                    String bucketName = cursor.getString(bucketNameColumn);
                    long dateTaken = cursor.getLong(dateTakenColumn);
                    String mimeType = cursor.getString(mimeTypeColumn);

                    Uri contentUri = ContentUris.withAppendedId(uri, mediaId);

                    MediaFolderBean folder = albumMap.get(bucketId);
                    if (folder != null) {
                        folder.itemCount += 1;
                        // 更新文件夹最新时间
                        if (dateTaken > folder.latestDateTaken) {
                            folder.latestDateTaken = dateTaken;
                            folder.coverUri = contentUri; // 覆盖最新封面
                            folder.mimeType = mimeType;
                        }
                    } else {
                        folder = new MediaFolderBean(bucketId, bucketName, contentUri, 1);
                        folder.latestDateTaken = dateTaken;
                        folder.mimeType = mimeType;
                        albumMap.put(bucketId, folder);
                    }
                }
            }
        } catch (SecurityException ignored) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    // endregion

    // region [Album Item] 获取相册文件夹内的 媒体列表
    private final MutableLiveData<List<MediaItemBean>> mediaItemsLiveData = new MutableLiveData<>();

    /**
     * 获取指定文件夹的所有媒体文件
     */
    public LiveData<List<MediaItemBean>> getMediaItemsLiveData() {
        return mediaItemsLiveData;
    }

    /**
     * 根据 bucketId 查询文件夹下所有图片和视频
     */
    public void loadMediaItems(Context context, String bucketId) {
        if (context == null || bucketId == null) {
            mediaItemsLiveData.postValue(new ArrayList<>());
            return;
        }


        executorService.execute(() -> {
            List<MediaItemBean> mediaItems = new ArrayList<>();

            // 图片
            String[] imageProjection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.IS_FAVORITE,// 收藏 (调查,能否根据收藏筛选, 2,能否通过API设置)
                    MediaStore.Images.Media.GENRE,// 流派
                    MediaStore.Images.Media.ORIENTATION,// 旋转角度

            };
            Uri imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

            // 视频
            String[] videoProjection = new String[]{
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATE_TAKEN,
                    MediaStore.Video.Media.DATE_MODIFIED,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT,
                    MediaStore.Images.Media.DATA,

                    MediaStore.Images.Media.IS_FAVORITE,// 收藏 (调查,能否根据收藏筛选, 2,能否通过API设置)
                    MediaStore.Images.Media.GENRE,// 流派
                    MediaStore.Images.Media.ORIENTATION,// 旋转角度

                    MediaStore.Video.Media.DURATION // 视频时长
            };
            Uri videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

            // 查询图片
            queryMediaItems(context, imageUri, imageProjection, bucketId, mediaItems, false);
            // 查询视频
            queryMediaItems(context, videoUri, videoProjection, bucketId, mediaItems, true);

            mediaItemsLiveData.postValue(mediaItems);
        });
    }

    private void queryMediaItems(Context context, Uri uri, String[] projection,
                                 String bucketId, List<MediaItemBean> resultList, boolean isVideo) {
        Cursor cursor = null;
        try {
            String selection = MediaStore.MediaColumns.BUCKET_ID + "=?";
            String[] selectionArgs = new String[]{bucketId};

            cursor = context.getContentResolver().query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    MediaStore.MediaColumns.DATE_TAKEN + " DESC" // 按拍摄时间倒序
            );

            if (cursor != null) {
                int idIndex = cursor.getColumnIndexOrThrow(projection[0]);
                int bucketIdIndex = cursor.getColumnIndexOrThrow(projection[1]);
                int bucketNameIndex = cursor.getColumnIndexOrThrow(projection[2]);
                int mimeTypeIndex = cursor.getColumnIndexOrThrow(projection[3]);
                int dateTakenIndex = cursor.getColumnIndexOrThrow(projection[4]);
                int dateModifiedIndex = cursor.getColumnIndexOrThrow(projection[5]);
                int sizeIndex = cursor.getColumnIndexOrThrow(projection[6]);
                int widthIndex = cursor.getColumnIndexOrThrow(projection[7]);
                int heightIndex = cursor.getColumnIndexOrThrow(projection[8]);
                int columnDataIndex = cursor.getColumnIndexOrThrow(projection[9]);

                int isFavoriteIndex = cursor.getColumnIndexOrThrow(projection[10]);
                int genreIndex = cursor.getColumnIndexOrThrow(projection[11]);
                int orientationIndex = cursor.getColumnIndexOrThrow(projection[12]);

                // 视频时长 index ，图片默认-1
                int durationIndex = -1;
                if (isVideo && projection.length > 13) {
                    durationIndex = cursor.getColumnIndexOrThrow(projection[13]);
                }
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIndex);
                    String bId = cursor.getString(bucketIdIndex);
                    String bName = cursor.getString(bucketNameIndex);
                    String mimeType = cursor.getString(mimeTypeIndex);
                    long dateTaken = cursor.getLong(dateTakenIndex);
                    long dateModified = cursor.getLong(dateModifiedIndex);
                    long size = cursor.getLong(sizeIndex);
                    int width = cursor.getInt(widthIndex);
                    int height = cursor.getInt(heightIndex);
                    String filePath = cursor.getString(columnDataIndex);


                    // 视频时长，图片默认0
                    int duration = 0;
                    if (durationIndex != -1) {
                        duration = cursor.getInt(durationIndex);
                    }
//                    if (isVideo) {
//                        String duration = AppUtilManager.getProperTime(cursor.getLong(columnDurationIndex) / ONE_THOUSAND_MILLISECONDS);
//                        galleryInfoEntity.setFileTime(TextUtils.isEmpty(duration) ? "0:00" : duration);
//                    } else {
//                        galleryInfoEntity.setFileTime("0:00");
//                    }

                    Uri contentUri = ContentUris.withAppendedId(uri, id);
                    MediaItemBean item = new MediaItemBean(id, bId, bName, contentUri, mimeType,
                            dateTaken, dateModified, size, width, height, duration);
                    item.filePath = filePath;

                    // EXIF
                    /*
                    // fileType
//                    String mimeType = context.getContentResolver().getType(uri);
                    int fileMimeTypeIndex = AppUtilManager.getFileTypeIndex(mimeType);
                    item.fileType = fileMimeTypeIndex ;
                    // rating
                    int rating = FileUtilManager.getInstance().getRating(item.fileType, item.filePath);
                    item.fileRating = rating;
                    // exif photoStyle
                    if (AppUtilManager.isExistExifInfo( item.filePath, item.fileType)) {
//                        galleryInfoEntity.setPhotoStyle(AppUtilManager.getPhotoStyleKey(LlcApplication.getContext().photoStyle, LlcApplication.getContext().modelOriginal));
                        item.photoStyle = AppUtilManager.getPhotoStyleKey(LlcApplication.getContext().photoStyle, LlcApplication.getContext().modelOriginal);
                    }

                    */

                    resultList.add(item);
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    // endregion

    // region [Album Zoom] 处理系统相册文件夹列表缩放

    public enum ZoomAction {
        /**
         * 列数增加
         */
        INCREASE,
        /**
         * 列数减少
         */
        DECREASE
    }

    private final MutableLiveData<ZoomAction> zoomActionLiveData = new MutableLiveData<>();

    /**
     * 列数增加
     */
    public void zoomIn() {
        zoomActionLiveData.setValue(ZoomAction.INCREASE);
    }

    /**
     * 列数减少
     */
    public void zoomOut() {
        zoomActionLiveData.setValue(ZoomAction.DECREASE);
    }

    public LiveData<ZoomAction> getZoomActionLiveData() {
        return zoomActionLiveData;
    }
    // endregion

    // region [EventFromParentEnum] 外部  → 内部子Fragment 传递的点击事件

    private final MutableLiveData<EventFromParentPost> parentEvent = new MutableLiveData<>();

    public void sendFromParentEvent(EventFromParentPost action) {
        parentEvent.setValue(action);
    }

    public LiveData<EventFromParentPost> getParentEvent() {
        return parentEvent;
    }

    // endregion

    // region [EventFromChild] 内部子Fragment -> 外部宿主Fragment传递操作View事件（eg：点击或setText）

    private final SingleLiveEvent<EventFromChildRequest> childEvent = new SingleLiveEvent<>();

    public void sendFromChildEvent(EventFromChildRequest event) {
        childEvent.setValue(event);
    }

    public LiveData<EventFromChildRequest> getChildEvent() {
        return childEvent;
    }
    // endregion

    // region [UiState] 缓存viewpager宿主fragment的UI控件visibility状态，子fragment可间接获取

    public int currentViewPagerIndex = 0;

    private final MutableLiveData<GalleryUiState> parentUiStateLiveData =
            new MutableLiveData<>(new GalleryUiState.Builder().build());

    /**
     * 间接获取 ViewContainerFragment `TitleBar`和`Filter筛选`区域，View的Visible状态
     */
    public LiveData<GalleryUiState> getParentUiStateLiveData() {
        return parentUiStateLiveData;
    }

    /**
     * 更新整个UI状态
     */
    public void updateUiState(GalleryUiState newState) {
        parentUiStateLiveData.setValue(newState);
    }

    /**
     * 更新  viewBinding.galleryFilterNotSelectedContainerIsVisible
     */
    public void galleryFilterNotSelectedContainerVisibility(boolean visible) {
        GalleryUiState oldState = parentUiStateLiveData.getValue();
        if (oldState != null) {
            GalleryUiState newState = new GalleryUiState.Builder(oldState)
                    .galleryFilterNotSelectedContainerIsVisible(visible)
                    .build();
            updateUiState(newState);
        }
    }

    /**
     * 更新  viewBinding.galleryFilterNotSelectedContainerIsVisible
     */
    public void galleryFilterSelectedContainerVisibility(boolean visible) {
        GalleryUiState oldState = parentUiStateLiveData.getValue();
        if (oldState != null) {
            GalleryUiState newState = new GalleryUiState.Builder(oldState)
                    .galleryFilterSelectedContainerIsVisible(visible)
                    .build();
            updateUiState(newState);
        }
    }

    /**
     * 更新 viewBinding.galleryFilter.getRoot() visibility 状态
     *
     * @param isVisible isVisible
     */
    public void galleryFilterRootVisibility(boolean isVisible) {
        GalleryUiState oldState = parentUiStateLiveData.getValue();
        if (oldState != null) {
            GalleryUiState newState = new GalleryUiState.Builder(oldState)
                    .galleryFilterRootIsVisible(isVisible)
                    .build();
            updateUiState(newState);
        }
    }

    /**
     * 更新 viewBinding.galleryTitleContainerIsVisible visibility 状态
     *
     * @param isVisible isVisible
     */
    public void galleryTitleContainerVisibility(boolean isVisible) {
        GalleryUiState oldState = parentUiStateLiveData.getValue();
        if (oldState != null) {
            GalleryUiState newState = new GalleryUiState.Builder(oldState)
                    .galleryTitleContainerIsVisible(isVisible)
                    .build();
            updateUiState(newState);
        }
    }

    /**
     * 更新 viewBinding.gallerySelectionTitle visibility 状态
     *
     * @param isVisible isVisible
     */
    public void gallerySelectionTitleRootVisibility(boolean isVisible) {
        GalleryUiState oldState = parentUiStateLiveData.getValue();
        if (oldState != null) {
            GalleryUiState newState = new GalleryUiState.Builder(oldState)
                    .gallerySelectionTitleRootIsVisible(isVisible)
                    .build();
            updateUiState(newState);
        }
    }

    // endregion

    // region [RegisterForActivityResult] 处理 ActivityResultLauncher 分发
    private final MutableLiveData<EventForActivityResultLauncher> activityResultEvent = new MutableLiveData<>();

    /**
     * 向内部 childFragment 发送 ActivityResult
     */
    public void sendActivityResultEvent(EventForActivityResultLauncher action) {
        activityResultEvent.setValue(action);
    }

    /**
     * 获取 ParentFragment 的 ActivityResult
     */
    public LiveData<EventForActivityResultLauncher> getParentResultLauncher() {
        return activityResultEvent;
    }
    // endregion

    // region[Bak]

    /**
     * 获取设备图片和视频，的文件夹分组列表，
     * 分组依据：BUCKET_ID
     * 文件夹名：BUCKET_DISPLAY_NAME
     */
    @Deprecated
    public void loadAlbumFolders_ExpandAll(Context context) {
        if (context == null) {
            albumFoldersLiveData.postValue(new ArrayList<>());
            return;
        }
        executorService.execute(() -> {
            Map<String, MediaFolderBean> albumMap = new HashMap<>();

            // 图片
            String[] imageProjection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.RELATIVE_PATH
            };
            // 图片数据源
            Uri imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            // 视频
            String[] videoProjection = new String[]{
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.RELATIVE_PATH
            };
            // 视频数据源
            Uri videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            // 查询图片
            queryMediaStore(context, imageUri, imageProjection, albumMap);
            // 查询视频
            queryMediaStore(context, videoUri, videoProjection, albumMap);
            albumFoldersLiveData.postValue(new ArrayList<>(albumMap.values()));
        });
    }

    // endregion
}
