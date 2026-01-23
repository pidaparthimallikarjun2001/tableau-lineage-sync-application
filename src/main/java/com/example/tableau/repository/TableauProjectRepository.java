package com.example.tableau.repository;

import com.example.tableau.entity.TableauProject;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableauProjectRepository extends JpaRepository<TableauProject, Long> {

    Optional<TableauProject> findByAssetIdAndSiteId(String assetId, String siteId);

    Optional<TableauProject> findByAssetId(String assetId);

    List<TableauProject> findBySiteId(String siteId);

    @Query("SELECT p FROM TableauProject p WHERE p.site.id = :siteDbId")
    List<TableauProject> findBySiteDbId(@Param("siteDbId") Long siteDbId);

    List<TableauProject> findByStatusFlag(StatusFlag statusFlag);

    List<TableauProject> findByStatusFlagNot(StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauProject p SET p.statusFlag = :statusFlag WHERE p.assetId = :assetId AND p.siteId = :siteId")
    int updateStatusFlagByAssetIdAndSiteId(@Param("assetId") String assetId, @Param("siteId") String siteId, @Param("statusFlag") StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauProject p SET p.statusFlag = :statusFlag WHERE p.site.id = :siteDbId")
    int updateStatusFlagBySiteDbId(@Param("siteDbId") Long siteDbId, @Param("statusFlag") StatusFlag statusFlag);

    @Query("SELECT p FROM TableauProject p WHERE p.statusFlag != 'DELETED'")
    List<TableauProject> findAllActive();

    @Query("SELECT p FROM TableauProject p WHERE p.siteId = :siteId AND p.statusFlag != 'DELETED'")
    List<TableauProject> findAllActiveBySiteId(@Param("siteId") String siteId);

    boolean existsByAssetIdAndSiteId(String assetId, String siteId);
}
